<template>
  <div class="trip-page">
    <el-card v-if="loading && !state">
      <el-skeleton :rows="6" animated />
    </el-card>

    <el-card v-else-if="state" class="trip-card">
      <template #header>
        <div class="trip-header">
          <div class="title-group">
            <span class="trip-title">{{ state.destination || '行程伴侣' }}</span>
            <el-tag size="small" :type="statusType(state.status)">
              {{ statusLabel(state.status) }}
            </el-tag>
            <el-tag v-if="state.currentDay" size="small" effect="plain">
              第 {{ state.currentDay }} 天
            </el-tag>
          </div>
          <el-button size="small" text @click="goGuidebook">返回攻略</el-button>
        </div>
      </template>

      <p v-if="state.startDate" class="date-range">
        {{ state.startDate }} 至 {{ state.endDate }} · 共 {{ state.totalCount }} 个节点
      </p>

      <div class="progress-block">
        <el-progress :percentage="progressPercent" :stroke-width="10" />
        <span class="muted">
          已执行 {{ state.executedCount }} · 跳过 {{ state.skippedCount }} · 改期
          {{ state.rescheduledCount }}
        </span>
      </div>

      <el-alert
        v-if="notConfirmed"
        type="info"
        show-icon
        :closable="false"
        title="攻略尚未确认"
        description="确认攻略并在出行日期内打开，才会推进执行状态。"
        class="trip-alert"
      />
      <el-alert
        v-else-if="!state.currentDay"
        type="info"
        show-icon
        :closable="false"
        title="当前不在出行日期内"
        description="出行当天会显示今日安排与调整建议。"
        class="trip-alert"
      />

      <h3 class="section-title">今日安排</h3>
      <div v-if="todayNodes.length" class="node-list">
        <div v-for="node in todayNodes" :key="node.nodeId" class="node-card">
          <div class="node-head">
            <div class="node-main">
              <strong>{{ node.placeName || '待定地点' }}</strong>
              <el-tag size="small" effect="plain">{{ typeLabel(node.type) }}</el-tag>
            </div>
            <span class="node-time">{{ timeRange(node) }}</span>
          </div>
          <p v-if="node.note" class="node-note">{{ node.note }}</p>
          <div class="node-status-row">
            <el-tag v-if="node.executionStatus" size="small" :type="statusTagType(node.executionStatus)">
              {{ EXECUTION_LABELS[node.executionStatus] }}
            </el-tag>
            <span v-if="node.markedAt" class="muted">更新于 {{ formatTime(node.markedAt) }}</span>
          </div>
          <div class="node-actions">
            <el-button
              size="small"
              type="success"
              plain
              :loading="markingId === node.nodeId"
              :disabled="isBusy"
              @click="onMark(node, 'executed')"
            >
              已执行
            </el-button>
            <el-button
              size="small"
              plain
              :loading="markingId === node.nodeId"
              :disabled="isBusy"
              @click="onMark(node, 'skipped')"
            >
              跳过
            </el-button>
            <el-button
              size="small"
              plain
              :loading="markingId === node.nodeId"
              :disabled="isBusy"
              @click="onMark(node, 'rescheduled')"
            >
              改期
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="今天没有行程安排" />

      <h3 class="section-title">调整建议</h3>
      <div v-if="suggestions.length" class="suggestion-list">
        <div v-for="item in suggestions" :key="item.suggestionId" class="suggestion-card">
          <div class="suggestion-head">
            <el-tag size="small" effect="plain">{{ suggestionTypeLabel(item.type) }}</el-tag>
            <span class="muted">{{ formatTime(item.createdAt) }}</span>
          </div>
          <p class="suggestion-reason">{{ item.reason }}</p>
          <p class="suggestion-action">{{ item.action }}</p>
          <div v-if="item.status === 'pending'" class="suggestion-actions">
            <el-button
              size="small"
              type="primary"
              :loading="decidingId === item.suggestionId"
              :disabled="isBusy"
              @click="onAccept(item)"
            >
              接受
            </el-button>
            <el-button
              size="small"
              :loading="decidingId === item.suggestionId"
              :disabled="isBusy"
              @click="onReject(item)"
            >
              拒绝
            </el-button>
          </div>
          <el-tag v-else-if="item.status === 'rejected'" size="small" type="info">已忽略</el-tag>
          <el-tag v-else-if="item.status === 'accepted'" size="small" type="success">
            已接受（提示，未改行程）
          </el-tag>
          <p v-if="conflictMessages[item.suggestionId]" class="conflict-inline">
            {{ conflictMessages[item.suggestionId] }}
          </p>
        </div>
      </div>
      <el-empty v-else description="暂时没有新的调整建议" />
    </el-card>

    <el-card v-else>
      <el-empty description="暂无行程信息">
        <el-button type="primary" @click="goGuidebook">返回攻略</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as companionApi from '@/api/companion'
import { ApiError } from '@/api/request'
import type { TodayNode, TripCompanion, TripSuggestion } from '@/types/api'

const route = useRoute()
const router = useRouter()

const STATUS_LABELS: Record<string, string> = {
  confirmed: '已确认',
  executing: '行程中',
  completed: '已完成',
  editing: '编辑中',
  draft: '草稿',
  generating: '生成中',
  failed: '生成失败'
}
const TYPE_LABELS: Record<string, string> = {
  activity: '活动',
  meal: '餐饮',
  transport: '交通',
  stay: '住宿',
  free: '自由活动'
}
const EXECUTION_LABELS: Record<string, string> = {
  executed: '已执行',
  skipped: '已跳过',
  rescheduled: '已改期'
}
const SUGGESTION_TYPE_LABELS: Record<string, string> = {
  weather_relocate: '天气调整',
  opening_hours: '营业时间',
  local_news: '当地动态'
}
const NOT_CONFIRMED = ['draft', 'editing', 'generating', 'failed']

const guidebookId = computed(() => {
  const id = route.params.guidebookId
  return typeof id === 'string' ? id : ''
})
const state = ref<TripCompanion | null>(null)
const loading = ref(false)
const markingId = ref<string | null>(null)
const decidingId = ref<string | null>(null)
const refreshing = ref(false)
const conflictMessages = reactive<Record<string, string>>({})

let pollTimer: number | null = null

const isBusy = computed(
  () => markingId.value != null || decidingId.value != null || refreshing.value
)
const todayNodes = computed(() => state.value?.todayNodes ?? [])
const suggestions = computed(() => state.value?.suggestions ?? [])
const progressPercent = computed(() =>
  Math.max(0, Math.min(100, Math.round(state.value?.progress ?? 0)))
)
const notConfirmed = computed(() => NOT_CONFIRMED.includes(state.value?.status ?? ''))

function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}
function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'executing') return 'success'
  if (status === 'confirmed') return 'warning'
  if (status === 'completed') return 'info'
  if (status === 'failed') return 'danger'
  return 'info'
}
function statusTagType(status: string): 'success' | 'warning' | 'info' {
  if (status === 'executed') return 'success'
  if (status === 'rescheduled') return 'warning'
  return 'info'
}
function typeLabel(type: string | null) {
  return type ? TYPE_LABELS[type] ?? type : '活动'
}
function suggestionTypeLabel(type: string) {
  return SUGGESTION_TYPE_LABELS[type] ?? type
}
function timeRange(node: TodayNode) {
  if (node.timeSlotStart && node.timeSlotEnd) {
    return `${node.timeSlotStart} - ${node.timeSlotEnd}`
  }
  return node.timeSlotStart ?? '时间待定'
}
function formatTime(value: string | null) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

async function load() {
  if (!guidebookId.value) return
  loading.value = true
  try {
    state.value = await companionApi.getTripCompanion(guidebookId.value)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    if (document.hidden || isBusy.value) return
    void load()
  }, 30000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 仅进入页面时刷新一次建议，避免手动刷新打爆扫描接口。 */
async function refreshOnce() {
  if (!guidebookId.value) return
  refreshing.value = true
  try {
    await companionApi.refreshSuggestions(guidebookId.value)
  } catch {
    // 扫描失败不影响页面，仍有上次的建议
  } finally {
    refreshing.value = false
  }
}

function bumpCount(status: string | null, delta: number) {
  const target = state.value
  if (!target || !status) return
  if (status === 'executed') target.executedCount = Math.max(0, target.executedCount + delta)
  else if (status === 'skipped') target.skippedCount = Math.max(0, target.skippedCount + delta)
  else if (status === 'rescheduled') {
    target.rescheduledCount = Math.max(0, target.rescheduledCount + delta)
  }
}

async function onMark(node: TodayNode, status: string) {
  if (!guidebookId.value || isBusy.value) return
  markingId.value = node.nodeId
  try {
    const res = await companionApi.markNodeStatus(guidebookId.value, node.nodeId, status)
    bumpCount(node.executionStatus, -1)
    bumpCount(res.status, 1)
    node.executionStatus = res.status as TodayNode['executionStatus']
    node.markedAt = res.markedAt
    if (state.value) state.value.progress = res.progress
  } catch {
    // 拦截器已提示，状态保持原样
  } finally {
    markingId.value = null
  }
}

async function onAccept(item: TripSuggestion) {
  if (!guidebookId.value || isBusy.value) return
  decidingId.value = item.suggestionId
  try {
    const res = await companionApi.acceptSuggestion(guidebookId.value, item.suggestionId)
    delete conflictMessages[item.suggestionId]
    if (res.applied) {
      ElMessage.success(`已调整，版本 v${res.version ?? '-'}`)
      await load()
    } else {
      item.status = 'accepted'
      ElMessage.success('已记录，请自行核实')
    }
  } catch (error) {
    if (error instanceof ApiError && error.code === 42200) {
      conflictMessages[item.suggestionId] = error.message
    }
  } finally {
    decidingId.value = null
  }
}

async function onReject(item: TripSuggestion) {
  if (!guidebookId.value || isBusy.value) return
  decidingId.value = item.suggestionId
  try {
    await companionApi.rejectSuggestion(guidebookId.value, item.suggestionId)
    item.status = 'rejected'
    ElMessage.success('已忽略该建议')
  } catch {
    // 拦截器已提示
  } finally {
    decidingId.value = null
  }
}

function goGuidebook() {
  if (guidebookId.value) {
    router.push(`/guidebook/${guidebookId.value}`)
  } else {
    router.push({ name: 'chat' })
  }
}

async function bootstrap() {
  if (!guidebookId.value) return
  await refreshOnce()
  await load()
  startPolling()
}

function reset() {
  stopPolling()
  state.value = null
  markingId.value = null
  decidingId.value = null
  refreshing.value = false
  Object.keys(conflictMessages).forEach((key) => delete conflictMessages[key])
}

onMounted(bootstrap)
watch(guidebookId, async (id) => {
  reset()
  if (id) await bootstrap()
})
onBeforeUnmount(reset)
</script>

<style scoped>
.trip-page {
  max-width: 860px;
  margin: 0 auto;
}
.trip-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
}
.title-group {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}
.trip-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}
.date-range {
  margin: 0 0 var(--space-3);
  font-size: 13px;
  color: var(--text-3);
}
.progress-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.trip-alert {
  margin-bottom: var(--space-4);
}
.section-title {
  margin: var(--space-5) 0 var(--space-3);
  font-size: 15px;
  color: var(--text-1);
}
.node-list,
.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.node-card,
.suggestion-card {
  padding: var(--space-3);
  border: 1px solid var(--line-1, #e5e7eb);
  border-radius: 10px;
  background: var(--bg-2, #fff);
}
.node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.node-main {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.node-time {
  font-size: 12px;
  color: var(--text-3);
}
.node-note {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}
.node-status-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.node-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.suggestion-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.suggestion-reason {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-1);
}
.suggestion-action {
  margin: var(--space-1) 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}
.suggestion-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.conflict-inline {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  color: var(--warning, #b45309);
}
.muted {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}
</style>

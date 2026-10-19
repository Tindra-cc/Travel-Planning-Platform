<template>
  <div class="guidebook-page">
    <template v-if="!guidebookId">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>旅行攻略</span>
          </div>
        </template>

        <div class="empty-state">
          <div class="empty-ico">🗺️</div>
          <h3>还没有生成攻略</h3>
          <p>在「对话」里描述你的旅行需求，信息收集完成后即可一键生成行程与攻略。</p>
          <el-button type="primary" @click="goChat">去对话</el-button>
        </div>
      </el-card>
    </template>

    <el-card v-else-if="loading && !detail">
      <el-skeleton :rows="8" animated />
    </el-card>

    <el-card v-else-if="detail" class="detail-card">
      <template #header>
        <div class="card-header">
          <div class="title-group">
            <span class="detail-title">{{ detail.title || '攻略生成中' }}</span>
            <el-tag size="small" :type="statusType(detail.status)">
              {{ statusLabel(detail.status) }}
            </el-tag>
            <el-tag v-if="detail.version" size="small" effect="plain">v{{ detail.version }}</el-tag>
          </div>
          <el-button size="small" text @click="goChat">返回对话</el-button>
        </div>
      </template>

      <!-- 生成中：WS 推送 + 轮询兜底 -->
      <div v-if="detail.status === 'generating'" class="generating">
        <el-progress
          type="dashboard"
          :percentage="progress"
          :width="148"
          :stroke-width="10"
          color="#3b82f6"
        />
        <div class="step-title">{{ stepLabel }}</div>
        <el-steps :active="stepIndex" align-center finish-status="success" class="steps">
          <el-step v-for="s in HIGH_LEVEL_STEPS" :key="s" :title="s" />
        </el-steps>
        <p class="generating-note">正在结合你的画像、目的地资料与实时数据生成，预计约 30 秒。</p>
      </div>

      <!-- 失败：可重试 -->
      <div v-else-if="detail.status === 'failed'" class="failed">
        <el-alert
          type="error"
          :title="detail.errorMessage || '攻略生成失败'"
          description="可以重新生成，已消耗的配额不会重复扣除。"
          show-icon
          :closable="false"
        />
        <div class="actions">
          <el-button type="primary" :loading="retrying" @click="onRetry">重新生成</el-button>
          <el-button @click="goChat">返回对话</el-button>
        </div>
      </div>

      <!-- 完成：日程 / 预算 / 贴士 / 溯源 -->
      <div v-else class="draft">
        <el-alert
          v-if="detail.degradedMode"
          type="warning"
          :closable="false"
          show-icon
          title="本次为降级生成"
          :description="degradationText"
          class="degraded-alert"
        />

        <el-tabs v-model="activeDay">
          <el-tab-pane
            v-for="day in days"
            :key="String(day.day)"
            :label="`第 ${day.day} 天`"
            :name="String(day.day)"
          >
            <el-timeline v-if="day.nodes?.length">
              <el-timeline-item
                v-for="node in day.nodes"
                :key="node.nodeId || String(node.place?.name)"
                :timestamp="timeRange(node)"
                placement="top"
              >
                <div class="node-card">
                  <div class="node-head">
                    <strong>{{ node.place?.name || '待定地点' }}</strong>
                    <el-tag size="small" effect="plain">{{ typeLabel(node.type) }}</el-tag>
                  </div>
                  <p v-if="node.note" class="node-note">{{ node.note }}</p>
                  <div class="node-source">
                    来源：{{ node.source || '通用知识' }}
                    <span v-if="node.dataFreshness"> · 数据时间 {{ node.dataFreshness }}</span>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="本日暂无安排" />
          </el-tab-pane>
        </el-tabs>

        <div class="info-grid">
          <section v-if="budget">
            <h3>预算拆解</h3>
            <el-table :data="budget.items ?? []" size="small" border>
              <el-table-column prop="category" label="类别" />
              <el-table-column label="最低" align="right">
                <template #default="{ row }">{{ money(row.min) }}</template>
              </el-table-column>
              <el-table-column label="最高" align="right">
                <template #default="{ row }">{{ money(row.max) }}</template>
              </el-table-column>
            </el-table>
            <p v-if="budget.total" class="budget-total">
              合计 {{ money(budget.total.min) }} - {{ money(budget.total.max) }}
              {{ budget.currency || 'CNY' }}
            </p>
          </section>

          <section class="tips">
            <h3>行前准备</h3>
            <ul v-if="detail.packingList?.length">
              <li v-for="item in detail.packingList" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="muted">暂无</p>

            <h3>文化贴士</h3>
            <ul v-if="detail.culturalTips?.length">
              <li v-for="item in detail.culturalTips" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="muted">暂无</p>

            <h3>避坑提示</h3>
            <ul v-if="detail.pitfallTips?.length">
              <li v-for="item in detail.pitfallTips" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="muted">暂无</p>
          </section>
        </div>

        <div class="actions">
          <el-button @click="onExport('markdown')">导出 Markdown</el-button>
          <el-button @click="onExport('html')">导出 HTML</el-button>
          <el-button type="danger" plain :loading="deleting" @click="onDelete">删除攻略</el-button>
        </div>

        <p v-if="detail.disclaimer" class="disclaimer">{{ detail.disclaimer }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as guidebookApi from '@/api/guidebook'
import { openGuidebookSocket, type GuidebookProgressEvent } from '@/utils/guidebookWs'
import type { BudgetBreakdown, GuidebookDay, GuidebookDetail, GuidebookNode } from '@/types/api'

const route = useRoute()
const router = useRouter()

const STATUS_LABELS: Record<string, string> = {
  generating: '生成中',
  draft: '草稿',
  failed: '生成失败',
  confirmed: '已确认',
  executing: '行程中',
  completed: '已完成'
}
const STEP_LABELS: Record<string, string> = {
  queued: '排队中',
  intent_validation: '校验意图',
  knowledge_retrieval: '检索目的地资料',
  travel_data: '获取天气与汇率',
  prompt_assembly: '组装生成提示',
  itinerary_compose: 'AI 生成行程',
  output_validation: '校验行程结构',
  traceability: '标注数据溯源',
  persist: '保存攻略',
  completed: '生成完成',
  failed: '生成失败'
}
const HIGH_LEVEL_STEPS = ['校验意图', '检索资料', '实时数据', 'AI 生成', '校验保存']
const TYPE_LABELS: Record<string, string> = {
  activity: '活动',
  meal: '餐饮',
  transport: '交通',
  stay: '住宿',
  free: '自由活动'
}
const DEGRADE_LABELS: Record<string, string> = {
  no_knowledge: '未检索到目的地资料',
  weather_unavailable: '天气数据不可用',
  exchange_unavailable: '汇率数据不可用',
  model_fallback: 'AI 模型不可用，已使用模板',
  json_repaired: 'AI 输出经过一次修复',
  validation_fallback: '行程校验失败，已使用模板',
  validation_repaired: '行程结构经过自动修复',
  day_count_mismatch: '生成天数与请求不一致',
  data_unavailable: '部分实时数据缺失'
}

const guidebookId = computed(() => {
  const id = route.params.guidebookId
  return typeof id === 'string' ? id : ''
})
const detail = ref<GuidebookDetail | null>(null)
const loading = ref(false)
const retrying = ref(false)
const deleting = ref(false)
const activeDay = ref('1')
let socket: WebSocket | null = null
let pollTimer: number | null = null

const days = computed<GuidebookDay[]>(() => detail.value?.days ?? [])
const budget = computed<BudgetBreakdown | null>(() => detail.value?.budgetBreakdown ?? null)
const progress = computed(() => Math.max(0, Math.min(100, detail.value?.progress ?? 0)))
const stepLabel = computed(() => STEP_LABELS[detail.value?.currentStep ?? ''] ?? '生成中')
const stepIndex = computed(() => {
  const step = detail.value?.currentStep ?? ''
  if (step === 'knowledge_retrieval') return 1
  if (step === 'travel_data' || step === 'prompt_assembly') return 2
  if (step === 'itinerary_compose') return 3
  if (step === 'output_validation' || step === 'traceability'
    || step === 'persist' || step === 'completed') return 5
  return 0
})
const degradationText = computed(() => {
  const reasons = detail.value?.degradationReasons ?? []
  if (!reasons.length) return '部分资料或实时数据缺失，内容仅供参考。'
  return reasons.map((r) => DEGRADE_LABELS[r] ?? r).join('；')
})

function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}
function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'draft') return 'info'
  if (status === 'generating') return 'warning'
  if (status === 'failed') return 'danger'
  return 'success'
}
function typeLabel(type: string | null) {
  return type ? TYPE_LABELS[type] ?? type : '活动'
}
function timeRange(node: GuidebookNode) {
  const { start, end } = node.timeSlot ?? {}
  if (start && end) return `${start} - ${end}`
  return start ?? '时间待定'
}
function money(value: number | null | undefined) {
  if (value == null) return '-'
  return value.toLocaleString('zh-CN')
}

function isGenerating() {
  return detail.value?.status === 'generating'
}

async function load() {
  if (!guidebookId.value) return
  loading.value = true
  try {
    detail.value = await guidebookApi.getGuidebook(guidebookId.value)
    if (detail.value?.days?.length) {
      const first = String(detail.value.days[0]?.day ?? 1)
      if (!detail.value.days.some((d) => String(d.day) === activeDay.value)) {
        activeDay.value = first
      }
    }
    if (isGenerating()) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function startPolling() {
  if (pollTimer != null) return
  pollTimer = window.setInterval(() => {
    if (!isGenerating()) {
      stopPolling()
      return
    }
    void load()
  }, 2000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function openSocket() {
  closeSocket()
  if (!guidebookId.value || !isGenerating()) return
  socket = openGuidebookSocket(guidebookId.value, {
    onProgress: (ev: GuidebookProgressEvent) => {
      if (!detail.value) return
      detail.value.currentStep = ev.currentStep ?? detail.value.currentStep
      detail.value.progress = ev.progress ?? detail.value.progress
      detail.value.status = ev.status
      if (ev.status !== 'generating') {
        stopPolling()
        void load()
      }
    },
    onDone: () => {
      closeSocket()
      stopPolling()
      ElMessage.success('攻略生成完成')
      void load()
    },
    onError: (ev) => {
      ElMessage.error(ev.message || '攻略生成失败')
      closeSocket()
      void load()
    },
    onClose: () => {
      socket = null
      if (isGenerating()) startPolling()
    }
  })
}

function closeSocket() {
  if (socket) {
    socket.close()
    socket = null
  }
}

async function onRetry() {
  if (!guidebookId.value) return
  retrying.value = true
  try {
    await guidebookApi.retryGuidebook(guidebookId.value)
    if (detail.value) {
      detail.value = {
        ...detail.value,
        status: 'generating',
        progress: 0,
        currentStep: 'queued',
        errorMessage: null
      }
    }
    ElMessage.success('已重新开始生成')
    openSocket()
    startPolling()
  } catch {
    // 拦截器已提示
  } finally {
    retrying.value = false
  }
}

async function onExport(format: 'markdown' | 'html') {
  if (!guidebookId.value) return
  try {
    const res = await guidebookApi.exportGuidebook(guidebookId.value, format)
    const blob = new Blob([res.content], {
      type: format === 'html' ? 'text/html;charset=utf-8' : 'text/markdown;charset=utf-8'
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = res.filename
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    // 拦截器已提示
  }
}

async function onDelete() {
  if (!guidebookId.value) return
  try {
    await ElMessageBox.confirm('确认删除该攻略？删除后不可恢复。', '提示', { type: 'warning' })
  } catch {
    return
  }
  deleting.value = true
  try {
    await guidebookApi.deleteGuidebook(guidebookId.value)
    ElMessage.success('已删除')
    await router.push({ name: 'chat' })
  } catch {
    // 拦截器已提示
  } finally {
    deleting.value = false
  }
}

function goChat() {
  const sessionId = typeof route.query.sessionId === 'string' ? route.query.sessionId : ''
  router.push(sessionId ? { path: '/chat', query: { sessionId } } : { path: '/chat' })
}

function reset() {
  closeSocket()
  stopPolling()
  detail.value = null
  activeDay.value = '1'
}

onMounted(async () => {
  if (guidebookId.value) {
    await load()
    openSocket()
  }
})

watch(guidebookId, async (id) => {
  reset()
  if (id) {
    await load()
    openSocket()
  }
})

onBeforeUnmount(reset)
</script>

<style scoped>
.guidebook-page {
  max-width: 1100px;
  margin: 0 auto;
}

.card-header {
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
.detail-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: 72px var(--space-6);
  text-align: center;
}
.empty-ico {
  font-size: 52px;
  line-height: 1;
}
.empty-state h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}
.empty-state p {
  margin: 0;
  max-width: 420px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-2);
}

.generating {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  padding: 40px var(--space-4);
}
.step-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.steps {
  width: 100%;
  max-width: 760px;
}
.generating-note {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.failed {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4) 0;
}

.degraded-alert {
  margin-bottom: var(--space-4);
}

.node-card {
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
.node-note {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}
.node-source {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--text-3);
}

.info-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: var(--space-5);
  margin-top: var(--space-5);
}
.info-grid h3 {
  margin: 0 0 var(--space-2);
  font-size: 15px;
  color: var(--text-1);
}
.info-grid h3:not(:first-child) {
  margin-top: var(--space-4);
}
.info-grid ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-2);
}
.budget-total {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}
.muted {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-5);
  flex-wrap: wrap;
}
.disclaimer {
  margin: var(--space-4) 0 0;
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 860px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div class="compare-page">
    <el-card v-if="loading && !detail">
      <el-skeleton :rows="8" animated />
    </el-card>

    <el-card v-else>
      <template #header>
        <div class="card-header">
          <div class="title-group">
            <span class="title">多方案对比</span>
            <el-tag v-if="detail" size="small" :type="statusType(detail.status)">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </div>
          <el-button size="small" @click="backToBase">返回攻略</el-button>
        </div>
      </template>

      <el-alert
        v-if="detail && (detail.status === 'partial' || detail.status === 'failed')"
        type="warning"
        show-icon
        :closable="false"
        :title="detail.errorMessage || '部分方案生成失败'"
        class="compare-alert"
      />

      <div v-if="detail" class="compare-grid">
        <section
          v-for="variant in detail.variants"
          :key="variant.guidebookId"
          class="variant-card"
        >
          <header class="variant-header">
            <div>
              <span class="variant-name">
                {{ VARIANT_LABELS[variant.variant] || variant.variant }}
              </span>
              <el-tag size="small" effect="plain" :type="statusType(variant.status)">
                {{ statusLabel(variant.status) }}
              </el-tag>
            </div>
            <el-tag size="small" type="info" effect="plain">
              {{ variant.dayCount }} 天
            </el-tag>
          </header>

          <h3 class="variant-title">{{ variant.title || '方案生成中' }}</h3>
          <p class="variant-budget">{{ budgetText(variant.totalBudget) }}</p>

          <el-progress
            v-if="variant.status === 'generating'"
            :percentage="variant.progress ?? 0"
            class="variant-progress"
          />

          <div v-if="variant.highlights?.length" class="highlights">
            <el-tag
              v-for="item in variant.highlights"
              :key="item"
              size="small"
              effect="plain"
            >
              {{ item }}
            </el-tag>
          </div>

          <el-alert
            v-if="variant.status === 'failed'"
            type="error"
            :closable="false"
            show-icon
            title="该方案生成失败"
            description="可在攻略列表中单独重试该方案。"
          />

          <template v-else-if="variantDetail(variant.guidebookId)">
            <el-checkbox-group v-model="selected" class="node-group">
              <div
                v-for="day in variantDetail(variant.guidebookId)?.days ?? []"
                :key="`${variant.guidebookId}-${day.day}`"
                class="compare-day"
              >
                <div class="day-title">第 {{ day.day }} 天</div>
                <el-checkbox
                  v-for="(node, index) in day.nodes ?? []"
                  :key="node.nodeId ?? `${day.day}-${index}`"
                  :value="node.nodeId ? pickKey(variant.guidebookId, node.nodeId) : `none-${variant.guidebookId}-${day.day}-${index}`"
                  :disabled="!node.nodeId"
                  class="node-item"
                >
                  <span class="node-time">
                    {{ node.timeSlot?.start ?? '--:--' }}-{{ node.timeSlot?.end ?? '--:--' }}
                  </span>
                  <span class="node-name">{{ node.place?.name || '待定地点' }}</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <p v-if="!variantDetail(variant.guidebookId)?.days?.length" class="muted">
              暂无行程内容
            </p>
          </template>
          <p v-else class="muted">正在加载行程…</p>
        </section>
      </div>

      <el-empty v-if="!detail && !loading" description="对比任务不存在或已删除" />
    </el-card>

    <div v-if="detail && picks.length" class="mix-bar">
      <div class="mix-info">
        <span>已选 {{ picks.length }} 个节点</span>
        <el-button size="small" text @click="selected = []">清空</el-button>
      </div>
      <el-button type="primary" :loading="mixing" @click="onMix">混搭所选</el-button>
    </div>

    <el-alert
      v-if="conflicts.length"
      type="error"
      show-icon
      :closable="false"
      class="conflict-panel"
      :title="`所选节点存在 ${conflicts.length} 处冲突，调整后重试`"
    >
      <ul class="conflict-list">
        <li v-for="(item, index) in conflicts" :key="`${item.type}-${index}`">
          {{ item.day ? `第 ${item.day} 天 · ` : '' }}{{ item.message }}
        </li>
      </ul>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as compareApi from '@/api/compare'
import * as guidebookApi from '@/api/guidebook'
import type {
  ComparisonDetail,
  ComparisonVariant,
  GuidebookDetail,
  MixPick,
  NodeConflict
} from '@/types/api'

const VARIANT_LABELS: Record<string, string> = {
  budget: '省钱优先',
  balanced: '均衡方案',
  luxury: '品质优先',
  mixed: '混搭方案'
}
const STATUS_LABELS: Record<string, string> = {
  processing: '生成中',
  completed: '已完成',
  partial: '部分完成',
  failed: '生成失败',
  generating: '生成中',
  draft: '草稿',
  editing: '编辑中',
  confirmed: '已确认'
}

const route = useRoute()
const router = useRouter()
const comparisonId = computed(() =>
  typeof route.params.comparisonId === 'string' ? route.params.comparisonId : ''
)

const detail = ref<ComparisonDetail | null>(null)
const variantDetails = ref<Record<string, GuidebookDetail>>({})
const loading = ref(false)
const selected = ref<string[]>([])
const mixing = ref(false)
const conflicts = ref<NodeConflict[]>([])

let pollTimer: number | null = null
let detailsLoading = false

const picks = computed<MixPick[]>(() =>
  selected.value.map((key) => {
    const index = key.indexOf(':')
    return {
      guidebookId: key.slice(0, index),
      nodeId: key.slice(index + 1)
    }
  })
)

function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}

function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'completed' || status === 'confirmed') return 'success'
  if (status === 'partial' || status === 'editing') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}

function pickKey(guidebookId: string, nodeId: string) {
  return `${guidebookId}:${nodeId}`
}

function variantDetail(guidebookId: string): GuidebookDetail | null {
  return variantDetails.value[guidebookId] ?? null
}

function budgetText(total: { min: number | null; max: number | null } | null) {
  if (!total || total.min == null || total.max == null) return '预算待生成'
  return `¥${total.min} - ¥${total.max}`
}

async function load() {
  if (!comparisonId.value) return
  if (!detail.value) loading.value = true
  try {
    detail.value = await compareApi.getComparison(comparisonId.value)
    await loadVariantDetails(detail.value.variants)
    if (detail.value.status === 'processing') {
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

async function loadVariantDetails(variants: ComparisonVariant[]) {
  if (detailsLoading) return
  const targets = variants.filter(
    (item) => !['generating', 'failed'].includes(item.status)
      && !variantDetails.value[item.guidebookId]
  )
  if (!targets.length) return
  detailsLoading = true
  try {
    const loaded = await Promise.all(targets.map(async (item) => {
      try {
        const data = await guidebookApi.getGuidebook(item.guidebookId)
        return [item.guidebookId, data] as const
      } catch {
        return null
      }
    }))
    const next = { ...variantDetails.value }
    loaded.forEach((entry) => {
      if (entry) next[entry[0]] = entry[1]
    })
    variantDetails.value = next
  } finally {
    detailsLoading = false
  }
}

function startPolling() {
  if (pollTimer != null) return
  pollTimer = window.setInterval(() => {
    void load()
  }, 2000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function onMix() {
  if (!comparisonId.value || !picks.value.length) return
  mixing.value = true
  conflicts.value = []
  try {
    const res = await compareApi.mixComparison(comparisonId.value, picks.value)
    if (res.status === 'blocked') {
      conflicts.value = res.conflicts ?? []
      ElMessage.warning('所选节点存在冲突，请调整后重试')
      return
    }
    if (res.guidebookId) {
      ElMessage.success('混搭攻略已生成')
      router.push(`/guidebook/${res.guidebookId}`)
    }
  } catch {
    // 拦截器已提示
  } finally {
    mixing.value = false
  }
}

function backToBase() {
  if (detail.value?.baseGuidebookId) {
    router.push(`/guidebook/${detail.value.baseGuidebookId}`)
    return
  }
  router.back()
}

watch(selected, () => {
  conflicts.value = []
})

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.compare-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 16px 96px;
}

.card-header,
.title-group,
.variant-header,
.mix-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.title-group {
  justify-content: flex-start;
}

.title {
  font-size: 18px;
  font-weight: 600;
}

.compare-alert {
  margin-bottom: 16px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 960px) {
  .compare-grid {
    grid-template-columns: 1fr;
  }
}

.variant-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.variant-name {
  font-weight: 600;
  margin-right: 8px;
}

.variant-title {
  margin: 0;
  font-size: 16px;
}

.variant-budget {
  margin: 0;
  color: var(--el-color-primary);
  font-weight: 600;
}

.highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.node-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 420px;
  overflow-y: auto;
}

.compare-day {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
}

.day-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}

.node-item {
  display: flex;
  height: auto;
  margin: 0;
}

.node-time {
  color: var(--el-text-color-secondary);
  margin-right: 6px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.mix-bar {
  position: fixed;
  left: 50%;
  bottom: 20px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  padding: 8px 18px;
  box-shadow: 0 6px 20px rgb(0 0 0 / 12%);
  z-index: 20;
}

.conflict-panel {
  margin-top: 16px;
}

.conflict-list {
  margin: 6px 0 0;
  padding-left: 18px;
}
</style>

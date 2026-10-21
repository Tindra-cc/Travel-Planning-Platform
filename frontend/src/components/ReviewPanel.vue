<template>
  <section class="review-panel">
    <header class="panel-title">
      <h3>复盘与评价</h3>
      <el-tag v-if="summary" size="small" type="info">
        {{ summary.summarySource === 'ai' ? 'AI 生成，仅供参考' : '模板摘要' }}
      </el-tag>
    </header>

    <el-skeleton v-if="loading" :rows="4" animated />

    <template v-else-if="summary">
      <el-row :gutter="12" class="stat-row">
        <el-col :span="6"><el-statistic title="总节点" :value="summary.totalCount" /></el-col>
        <el-col :span="6"><el-statistic title="已执行" :value="summary.executedCount" /></el-col>
        <el-col :span="6"><el-statistic title="跳过" :value="summary.skippedCount" /></el-col>
        <el-col :span="6"><el-statistic title="改期" :value="summary.rescheduledCount" /></el-col>
      </el-row>

      <p v-if="summary.untouchedCount > 0" class="untouched-note">
        另有 {{ summary.untouchedCount }} 个节点未标记，未计入执行结果。
      </p>

      <div class="summary-box">
        <p>{{ summary.aiSummary }}</p>
      </div>

      <el-form label-width="96px" class="review-form">
        <el-form-item label="总体评分" required>
          <el-rate v-model="form.overallScore" :max="5" show-score />
        </el-form-item>
        <el-form-item label="AI 准确度">
          <el-rate v-model="form.aiAccuracyScore" :max="5" clearable />
        </el-form-item>
        <el-form-item label="执行贴合度">
          <el-rate v-model="form.nodeExecutionScore" :max="5" clearable />
        </el-form-item>
        <el-form-item label="评价">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="这次行程哪里最符合预期？哪里需要改进？"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!form.overallScore"
            @click="onSubmit"
          >
            {{ summary.review ? '更新评价' : '提交评价' }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-card v-if="showSuggestion" class="suggestion-card" shadow="never">
        <template #header>画像建议</template>
        <ul class="suggestion-fields">
          <li v-for="(text, key) in suggestionTexts" :key="key">{{ text }}</li>
        </ul>
        <p class="suggestion-reason">{{ summary.profileSuggestion?.reason }}</p>
        <div class="suggestion-actions">
          <el-button type="primary" :loading="deciding" @click="onDecide(true)">
            接受并更新画像
          </el-button>
          <el-button :loading="deciding" @click="onDecide(false)">暂不调整</el-button>
        </div>
      </el-card>

      <el-alert
        v-else-if="summary.profileSuggestion && summary.review"
        type="success"
        :closable="false"
        show-icon
        :title="decisionTitle"
      />
    </template>

    <el-empty v-else description="复盘数据加载失败">
      <el-button type="primary" @click="loadSummary">重新加载</el-button>
    </el-empty>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { decideProfileIteration, getReviewSummary, submitReview } from '@/api/review'
import type { ReviewSummary } from '@/types/api'

defineOptions({ name: 'ReviewPanel' })

const props = defineProps<{ guidebookId: string }>()

const summary = ref<ReviewSummary | null>(null)
const loading = ref(false)
const submitting = ref(false)
const deciding = ref(false)

const form = reactive({
  overallScore: 0,
  aiAccuracyScore: 0,
  nodeExecutionScore: 0,
  content: ''
})

const PACE_LABELS: Record<string, string> = {
  tight: '紧凑',
  balanced: '适中',
  relaxed: '轻松'
}
const BUDGET_LABELS: Record<string, string> = {
  budget: '穷游',
  economy: '经济',
  balanced: '平衡',
  comfort: '舒适',
  luxury: '奢华'
}

const showSuggestion = computed(() => {
  const s = summary.value
  return !!s?.profileSuggestion && s.review?.iterationStatus === 'pending'
})

const suggestionTexts = computed(() => {
  const fields = summary.value?.profileSuggestion?.fields ?? {}
  const texts: string[] = []
  if (typeof fields.pace === 'string') {
    texts.push(`节奏：${PACE_LABELS[fields.pace] ?? fields.pace}`)
  }
  if (typeof fields.budgetLevel === 'string') {
    texts.push(`预算档位：${BUDGET_LABELS[fields.budgetLevel] ?? fields.budgetLevel}`)
  }
  if (Array.isArray(fields.interestTags)) {
    texts.push(`兴趣标签：${fields.interestTags.join('、')}`)
  }
  if (typeof fields.tripDays === 'number') {
    texts.push(`常规天数：${fields.tripDays} 天`)
  }
  return texts
})

const decisionTitle = computed(() => {
  const review = summary.value?.review
  if (review?.iterationStatus === 'accepted') {
    return `已接受建议，画像更新到 v${review.profileVersionAfter}`
  }
  if (review?.iterationStatus === 'rejected') {
    return '已保留当前画像设置'
  }
  return ''
})

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await getReviewSummary(props.guidebookId)
    fillForm()
  } catch (error) {
    summary.value = null
    ElMessage.error(errorText(error, '复盘数据加载失败'))
  } finally {
    loading.value = false
  }
}

function fillForm() {
  const review = summary.value?.review
  form.overallScore = review?.overallScore ?? 0
  form.aiAccuracyScore = review?.aiAccuracyScore ?? 0
  form.nodeExecutionScore = review?.nodeExecutionScore ?? 0
  form.content = review?.content ?? ''
}

async function onSubmit() {
  if (!form.overallScore) {
    ElMessage.warning('请先选择总体评分')
    return
  }
  submitting.value = true
  try {
    await submitReview({
      guidebookId: props.guidebookId,
      overallScore: form.overallScore,
      aiAccuracyScore: form.aiAccuracyScore || null,
      nodeExecutionScore: form.nodeExecutionScore || null,
      content: form.content.trim() || null
    })
    ElMessage.success('评价已保存')
    await loadSummary()
  } catch (error) {
    ElMessage.error(errorText(error, '评价保存失败'))
  } finally {
    submitting.value = false
  }
}

async function onDecide(accept: boolean) {
  deciding.value = true
  try {
    const res = await decideProfileIteration(props.guidebookId, accept)
    ElMessage.success(accept ? `画像已更新到 v${res.profileVersion}` : '已保留当前画像')
    await loadSummary()
  } catch (error) {
    ElMessage.error(errorText(error, '操作失败，请刷新后重试'))
  } finally {
    deciding.value = false
  }
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

watch(() => props.guidebookId, loadSummary)
onMounted(loadSummary)
</script>

<style scoped>
.review-panel {
  margin-top: 28px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.panel-title h3 {
  margin: 0;
}
.stat-row {
  margin-bottom: 12px;
}
.untouched-note {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 8px 0;
}
.summary-box {
  background: var(--el-fill-color-light);
  padding: 12px 16px;
  border-radius: 6px;
  margin: 12px 0 20px;
}
.summary-box p {
  margin: 0;
}
.review-form {
  max-width: 640px;
}
.suggestion-card {
  margin-top: 16px;
}
.suggestion-fields {
  margin: 0 0 8px;
  padding-left: 20px;
}
.suggestion-reason {
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}
.suggestion-actions {
  display: flex;
  gap: 8px;
}
</style>

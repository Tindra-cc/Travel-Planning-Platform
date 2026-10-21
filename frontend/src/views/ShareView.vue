<template>
  <div class="share-page">
    <el-skeleton v-if="loading" :rows="8" animated />

    <el-result
      v-else-if="error"
      icon="warning"
      :title="errorTitle"
      :sub-title="errorText"
    >
      <template #extra>
        <el-button @click="load">重新加载</el-button>
      </template>
    </el-result>

    <template v-else-if="data">
      <header class="share-header">
        <h2>{{ data.title || '旅行攻略' }}</h2>
        <div class="meta">
          <el-tag size="small" type="info">只读分享</el-tag>
          <span v-if="data.guidebookVersion">版本 v{{ data.guidebookVersion }}</span>
          <span v-if="data.sharedAt">分享于 {{ data.sharedAt }}</span>
          <span v-if="data.travelDataAt">数据采集于 {{ data.travelDataAt }}</span>
        </div>
        <el-alert
          v-if="data.degradedMode"
          type="warning"
          :closable="false"
          show-icon
          title="该攻略部分实时数据缺失，请在出行前核实。"
        />
      </header>

      <GuidebookDays :days="data.days" />

      <section v-if="data.budgetBreakdown" class="budget">
        <h3>预算</h3>
        <p v-if="data.budgetBreakdown.total">
          {{ data.budgetBreakdown.total.min }} - {{ data.budgetBreakdown.total.max }}
          {{ data.budgetBreakdown.currency || 'CNY' }}
        </p>
      </section>

      <section v-if="data.packingList?.length" class="tips">
        <h3>行前准备</h3>
        <ul>
          <li v-for="item in data.packingList" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section v-if="data.culturalTips?.length" class="tips">
        <h3>文化提示</h3>
        <ul>
          <li v-for="tip in data.culturalTips" :key="tip">{{ tip }}</li>
        </ul>
      </section>

      <section v-if="data.pitfallTips?.length" class="tips">
        <h3>避坑提示</h3>
        <ul>
          <li v-for="tip in data.pitfallTips" :key="tip">{{ tip }}</li>
        </ul>
      </section>

      <p v-if="data.disclaimer" class="disclaimer">{{ data.disclaimer }}</p>

      <div class="actions">
        <el-button type="primary" size="large" @click="onRegenerate">
          为我再生成
        </el-button>
      </div>

      <el-dialog v-model="sessionDialog" title="选择用于生成的会话" width="520px">
        <el-empty v-if="!readySessions.length" description="没有可用的会话">
          <el-button type="primary" @click="goChat">去创建一个</el-button>
        </el-empty>
        <el-radio-group v-else v-model="selectedSessionId" class="session-list">
          <el-radio
            v-for="session in readySessions"
            :key="session.sessionId"
            :value="session.sessionId"
            border
          >
            {{ session.title }}（{{ session.updatedAt }}）
          </el-radio>
        </el-radio-group>
        <template #footer>
          <el-button @click="sessionDialog = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!selectedSessionId"
            :loading="regenerating"
            @click="submitRegenerate"
          >
            开始生成
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="diffDialog" title="已开始生成" width="560px">
        <p v-if="diff.length">
          以下 {{ diff.length }} 个节点的实时数据已超过 48 小时或缺失，新攻略会重新采集：
        </p>
        <p v-else>分享中的数据仍然较新，新攻略仍会按当前数据重新生成。</p>
        <ul class="diff-list">
          <li v-for="item in diff" :key="item.nodeId">
            {{ item.nodeId }}：{{ item.old || '无采集时间' }} → 待刷新
          </li>
        </ul>
        <template #footer>
          <el-button type="primary" @click="goNewGuidebook">查看生成进度</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSharedGuidebook, regenerateFromShare } from '@/api/share'
import { listSessions } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/request'
import type {
  FreshnessDiffItem,
  SessionListItem,
  SharedGuidebook
} from '@/types/api'
import GuidebookDays from '@/components/GuidebookDays.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const data = ref<SharedGuidebook | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)
const sessionDialog = ref(false)
const diffDialog = ref(false)
const readySessions = ref<SessionListItem[]>([])
const selectedSessionId = ref('')
const regenerating = ref(false)
const diff = ref<FreshnessDiffItem[]>([])
const newGuidebookId = ref('')

const shareCode = computed(() => String(route.params.shareCode ?? ''))
const errorTitle = computed(() => {
  if (error.value?.code === 40900) return '分享不可用'
  if (error.value?.code === 40400) return '分享不存在'
  return '加载失败'
})
const errorText = computed(() => error.value?.message ?? '请检查链接是否正确')

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await getSharedGuidebook(shareCode.value)
  } catch (e) {
    data.value = null
    error.value = e instanceof ApiError ? e : new ApiError(-1, '请检查网络后重试')
  } finally {
    loading.value = false
  }
}

async function onRegenerate() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const page = await listSessions(1, 50)
    readySessions.value = page.list.filter((s) => s.status === 'ready_to_generate')
    selectedSessionId.value = ''
    sessionDialog.value = true
  } catch {
    // 拦截器已提示
  }
}

async function submitRegenerate() {
  if (!selectedSessionId.value) return
  regenerating.value = true
  try {
    const res = await regenerateFromShare(shareCode.value, selectedSessionId.value)
    newGuidebookId.value = res.guidebookId
    diff.value = res.dataFreshnessDiff
    sessionDialog.value = false
    diffDialog.value = true
  } catch {
    // 拦截器已提示；保留已选会话，方便重试
  } finally {
    regenerating.value = false
  }
}

function goNewGuidebook() {
  if (!newGuidebookId.value) return
  router.push({
    name: 'guidebook-detail',
    params: { guidebookId: newGuidebookId.value }
  })
}

function goChat() {
  router.push({ name: 'chat' })
}

onMounted(load)
</script>

<style scoped>
.share-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px;
}
.share-header {
  margin-bottom: 24px;
}
.share-header h2 {
  margin: 0 0 var(--space-2);
  font-size: 24px;
  color: var(--text-1);
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 8px 0;
  font-size: 13px;
  color: var(--text-3);
}
.budget,
.tips {
  margin-top: 24px;
}
.budget h3,
.tips h3 {
  margin: 0 0 var(--space-2);
  font-size: 15px;
  color: var(--text-1);
}
.budget p {
  margin: 0;
  font-size: 14px;
  color: var(--text-2);
}
.tips ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-2);
}
.disclaimer {
  margin-top: 24px;
  font-size: 12px;
  color: var(--text-3);
}
.actions {
  margin: 32px 0;
  text-align: center;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.diff-list {
  max-height: 240px;
  overflow: auto;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-2);
}
</style>

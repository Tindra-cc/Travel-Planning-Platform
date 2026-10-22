<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/request'
import * as expertApi from '@/api/expert'
import { listGuidebooks } from '@/api/guidebook'
import type {
  AuthorizeIssued,
  BatchIntent,
  BatchTask,
  EarningsPage,
  ExpertClientAccessLog,
  ExpertClientItem,
  ExpertPublicationItem,
  GuidebookListItem
} from '@/types/api'

const auth = useAuthStore()
const router = useRouter()

const activeTab = ref('clients')
const permissionDenied = ref(false)
const loading = ref(false)

const clients = ref<ExpertClientItem[]>([])
const publications = ref<ExpertPublicationItem[]>([])
const earnings = ref<EarningsPage>({
  list: [],
  page: 1,
  pageSize: 20,
  total: 0,
  settlementAvailable: false
})

const eligible = computed(() => auth.userInfo?.expertEligible === true)

const STATUS_LABELS: Record<string, string> = {
  pending_review: '待审核',
  published: '已发布',
  rejected: '已驳回',
  offline: '已下线'
}
const STATUS_TYPES: Record<string, string> = {
  pending_review: 'warning',
  published: 'success',
  rejected: 'danger',
  offline: 'info'
}
const AUTH_LABELS: Record<string, string> = {
  active: '已授权',
  pending: '待客户确认',
  none: '未授权'
}
const ACTION_LABELS: Record<string, string> = {
  view_client: '查看客户',
  read_profile: '读取画像',
  generate_guidebook: '生成攻略',
  list_guidebooks: '查看攻略列表'
}
const SCOPE_LABELS: Record<string, string> = {
  profile: '画像',
  guidebooks: '攻略'
}

function statusLabel(status: string) {
  return STATUS_LABELS[status] || status
}

function statusType(status: string) {
  return STATUS_TYPES[status] || 'info'
}

function authLabel(status: string | null) {
  return AUTH_LABELS[status || 'none'] || status
}

function actionLabel(action: string) {
  return ACTION_LABELS[action] || action
}

function scopeLabel(scope: string | null) {
  return scope ? SCOPE_LABELS[scope] || scope : '—'
}

function handlePermissionError(error: unknown) {
  if (error instanceof ApiError && error.code === 40300) {
    permissionDenied.value = true
    return true
  }
  return false
}

// ---- 客户 ----

async function loadClients() {
  loading.value = true
  try {
    const page = await expertApi.listExpertClients(1, 20)
    clients.value = page.list
  } catch (error) {
    if (!handlePermissionError(error)) throw error
  } finally {
    loading.value = false
  }
}

const clientDialogVisible = ref(false)
const clientForm = reactive({
  name: '',
  contact: '',
  note: '',
  linkedUserId: ''
})

function openClientDialog() {
  clientForm.name = ''
  clientForm.contact = ''
  clientForm.note = ''
  clientForm.linkedUserId = ''
  clientDialogVisible.value = true
}

async function submitClient() {
  if (!clientForm.name.trim()) {
    ElMessage.warning('请填写客户名称')
    return
  }
  const created = await expertApi.createExpertClient({
    name: clientForm.name.trim(),
    contact: clientForm.contact.trim() || null,
    note: clientForm.note.trim() || null,
    linkedUserId: clientForm.linkedUserId.trim() || null
  })
  clients.value.unshift(created)
  clientDialogVisible.value = false
  ElMessage.success('客户已创建')
}

// 客户详情与访问审计
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ExpertClientItem | null>(null)
const accessLogs = ref<ExpertClientAccessLog[]>([])

async function openDetail(client: ExpertClientItem) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  accessLogs.value = []
  try {
    const [info, logs] = await Promise.all([
      expertApi.getExpertClient(client.clientId),
      expertApi.listClientAccessLogs(client.clientId, 1, 20)
    ])
    detail.value = info
    accessLogs.value = logs.list
  } finally {
    detailLoading.value = false
  }
}

// 授权：明文令牌只保存在内存，关闭弹窗即清空
const authorizeVisible = ref(false)
const authorizeTarget = ref<ExpertClientItem | null>(null)
const authorizeScope = ref<string[]>(['profile', 'guidebooks'])
const tokenVisible = ref(false)
const issuedToken = ref<AuthorizeIssued | null>(null)

function openAuthorize(client: ExpertClientItem) {
  authorizeTarget.value = client
  authorizeScope.value = ['profile', 'guidebooks']
  authorizeVisible.value = true
}

async function submitAuthorize() {
  if (!authorizeTarget.value) return
  if (authorizeScope.value.length === 0) {
    ElMessage.warning('请至少选择一个授权范围')
    return
  }
  issuedToken.value = await expertApi.authorizeClient(
    authorizeTarget.value.clientId,
    authorizeScope.value
  )
  authorizeVisible.value = false
  tokenVisible.value = true
  await loadClients()
}

async function copyToken() {
  if (!issuedToken.value) return
  try {
    await navigator.clipboard.writeText(issuedToken.value.confirmToken)
    ElMessage.success('已复制，请通过安全渠道转交客户')
  } catch {
    ElMessage.warning('当前环境无法访问剪贴板，请手动选择文本复制')
  }
}

function closeTokenDialog() {
  tokenVisible.value = false
  issuedToken.value = null
}

// 批量生成：表单用 dateRange，提交时再映射为接口的 dateWindow
interface BatchIntentForm {
  destination: string
  dateRange: [string, string] | null
  adults: number
  amount: number
}

const batchVisible = ref(false)
const batchTarget = ref<ExpertClientItem | null>(null)
const batchIntents = ref<BatchIntentForm[]>([])
const batchResult = ref<BatchTask[]>([])

function emptyIntent(): BatchIntentForm {
  return {
    destination: '',
    dateRange: null,
    adults: 2,
    amount: 0
  }
}

function openBatch(client: ExpertClientItem) {
  batchTarget.value = client
  batchIntents.value = [emptyIntent()]
  batchResult.value = []
  batchVisible.value = true
}

function addIntent() {
  if (batchIntents.value.length >= 5) {
    ElMessage.warning('一次最多生成 5 本攻略')
    return
  }
  batchIntents.value.push(emptyIntent())
}

function removeIntent(index: number) {
  batchIntents.value.splice(index, 1)
}

async function submitBatch() {
  if (!batchTarget.value) return
  if (batchIntents.value.length < 1) {
    ElMessage.warning('请至少保留 1 个意图')
    return
  }
  if (batchIntents.value.length > 5) {
    ElMessage.warning('一次最多生成 5 本攻略')
    return
  }
  const missing = batchIntents.value.find(
    (item) => !item.destination.trim() || !item.dateRange?.[0] || !item.dateRange?.[1]
  )
  if (missing) {
    ElMessage.warning('请补全目的地与日期范围')
    return
  }
  const intents: BatchIntent[] = batchIntents.value.map((item) => ({
    destination: item.destination.trim(),
    dateWindow: { start: item.dateRange![0], end: item.dateRange![1] },
    party: { adults: item.adults },
    budget: { amount: item.amount, currency: 'CNY' }
  }))
  const res = await expertApi.batchGenerate(batchTarget.value.clientId, intents)
  batchResult.value = res.tasks
  ElMessage.success(`已提交 ${res.tasks.length} 本攻略`)
}

function openTask(taskId: string) {
  router.push(`/guidebook/${taskId}`)
}

// ---- 发布 ----

const publishVisible = ref(false)
const publishable = ref<GuidebookListItem[]>([])
const publishForm = reactive({
  guidebookId: '',
  title: '',
  description: '',
  coverUrl: '',
  priceYuan: 0
})

async function loadPublications() {
  const page = await expertApi.listPublications(1, 20)
  publications.value = page.list
}

async function openPublish() {
  const [page, pubs] = await Promise.all([
    listGuidebooks(undefined, 1, 50),
    expertApi.listPublications(1, 100)
  ])
  // 已有待审/已发布记录的攻略不能重复发布，避免提交后才报冲突
  const activeIds = new Set(
    pubs.list
      .filter((item) => ['pending_review', 'published'].includes(item.status))
      .map((item) => item.guidebookId)
  )
  publishable.value = page.list.filter(
    (item) =>
      ['draft', 'confirmed', 'executing', 'completed'].includes(item.status) &&
      !activeIds.has(item.guidebookId)
  )
  publishForm.guidebookId = ''
  publishForm.title = ''
  publishForm.description = ''
  publishForm.coverUrl = ''
  publishForm.priceYuan = 0
  publishVisible.value = true
}

function onGuidebookChange(guidebookId: string) {
  const item = publishable.value.find((row) => row.guidebookId === guidebookId)
  publishForm.title = item?.title || ''
}

async function submitPublish() {
  if (!publishForm.guidebookId) {
    ElMessage.warning('请选择要发布的攻略')
    return
  }
  if (!publishForm.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  const res = await expertApi.publishGuidebook(publishForm.guidebookId, {
    title: publishForm.title.trim(),
    description: publishForm.description.trim() || null,
    coverUrl: publishForm.coverUrl.trim() || null,
    priceCents: expertApi.yuanToCents(publishForm.priceYuan)
  })
  ElMessage.success(res.status === 'pending_review' ? '已提交审核' : '已发布')
  publishVisible.value = false
  await loadPublications()
}

async function offline(item: ExpertPublicationItem) {
  await ElMessageBox.confirm('下线后市场将立即不可见，确定继续？', '确认下线')
  await expertApi.offlinePublication(item.publicationId)
  ElMessage.success('已下线')
  await loadPublications()
}

// ---- 收益 ----

async function loadEarnings() {
  earnings.value = await expertApi.listEarnings(1, 20)
}

function money(cents: number) {
  return (cents / 100).toFixed(2)
}

// ---- 初始化与 Tab ----

async function onTabChange(name: string | number) {
  if (name === 'publications') {
    await loadPublications()
  } else if (name === 'earnings') {
    await loadEarnings()
  }
}

onMounted(async () => {
  if (!auth.userInfo) {
    await auth.fetchMe()
  }
  if (!eligible.value) {
    permissionDenied.value = true
    return
  }
  await loadClients()
})
</script>

<template>
  <div class="expert-view">
    <el-result
      v-if="permissionDenied"
      icon="warning"
      title="需要达人权限"
      sub-title="达人工作台面向 L3 达人开放。如需使用，请联系平台运营开通。"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/guidebook')">返回我的攻略</el-button>
      </template>
    </el-result>

    <template v-else>
      <div class="page-head">
        <h2>达人工作台</h2>
        <el-button :loading="loading" @click="loadClients">刷新</el-button>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="客户" name="clients">
          <div class="toolbar">
            <el-button type="primary" @click="openClientDialog">新建客户</el-button>
          </div>

          <el-table :data="clients" v-loading="loading" empty-text="暂无客户">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column
              prop="contact"
              label="联系方式"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column label="绑定账号" min-width="120">
              <template #default="{ row }">
                {{ row.linkedUserId || '未绑定' }}
              </template>
            </el-table-column>
            <el-table-column label="授权" width="120">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">
                  {{ authLabel(row.authorizationStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openDetail(row)">详情</el-button>
                <el-button size="small" @click="openAuthorize(row)">发起授权</el-button>
                <el-button size="small" @click="openBatch(row)">批量生成</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发布" name="publications">
          <div class="toolbar">
            <el-button type="primary" @click="openPublish">发布新攻略</el-button>
          </div>

          <el-table :data="publications" empty-text="暂无发布记录">
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column label="价格" width="120">
              <template #default="{ row }">¥ {{ money(row.priceCents) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核意见" min-width="180">
              <template #default="{ row }">
                <span v-if="row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'rejected'"
                  size="small"
                  :disabled="row.status === 'offline'"
                  @click="offline(row)"
                >
                  下线
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="收益" name="earnings">
          <el-alert
            v-if="!earnings.settlementAvailable"
            type="info"
            :closable="false"
            show-icon
            title="收益为占位展示"
            description="平台尚未接入订单、支付与结算，当前数据不代表真实应收；接入后以结算单为准。"
            class="earnings-alert"
          />

          <el-table :data="earnings.list" empty-text="暂无收益记录">
            <el-table-column prop="guideTitle" label="攻略" min-width="200" />
            <el-table-column label="金额" width="140">
              <template #default="{ row }">¥ {{ money(row.amountCents) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                {{ row.status === 'settled' ? '已结算' : '待结算' }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-dialog v-model="clientDialogVisible" title="新建客户" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="clientForm.name" maxlength="80" placeholder="客户称呼" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="clientForm.contact" maxlength="120" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="clientForm.note" type="textarea" maxlength="500" />
        </el-form-item>
        <el-form-item label="绑定账号">
          <el-input v-model="clientForm.linkedUserId" placeholder="可留空；填写后不可在此修改" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clientDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitClient">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="客户详情" size="520px">
      <el-skeleton v-if="detailLoading" :rows="4" animated />
      <template v-else-if="detail">
        <el-descriptions :column="1" border label-width="92px">
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ detail.contact || '—' }}</el-descriptions-item>
          <el-descriptions-item label="绑定账号">
            {{ detail.linkedUserId || '未绑定' }}
          </el-descriptions-item>
          <el-descriptions-item label="授权状态">
            {{ authLabel(detail.authorizationStatus) }}
          </el-descriptions-item>
          <el-descriptions-item label="授权范围">
            {{ detail.authorizedScopes?.map(scopeLabel).join(' / ') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.note || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">访问审计</h4>
        <el-table :data="accessLogs" size="small" empty-text="暂无访问记录">
          <el-table-column label="动作" min-width="130">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column label="范围" width="80">
            <template #default="{ row }">{{ scopeLabel(row.scope) }}</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="createdAt" label="时间" min-width="150" />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="authorizeVisible" title="发起授权" width="420px">
      <p class="dialog-hint">
        客户需使用确认令牌完成授权，之后你才能读取其画像与生成攻略。
      </p>
      <el-checkbox-group v-model="authorizeScope">
        <el-checkbox value="profile">画像</el-checkbox>
        <el-checkbox value="guidebooks">攻略</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="authorizeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAuthorize">生成令牌</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="tokenVisible"
      title="确认令牌"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      @closed="closeTokenDialog"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="关闭后无法再次查看，只能重新发起授权"
      />
      <div v-if="issuedToken" class="token-box">
        <code class="token-text">{{ issuedToken.confirmToken }}</code>
        <p class="dialog-hint">有效期至 {{ issuedToken.expiresAt }}</p>
      </div>
      <template #footer>
        <el-button @click="copyToken">复制令牌</el-button>
        <el-button type="primary" @click="closeTokenDialog">我已转交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量生成攻略" width="760px">
      <div v-for="(intent, index) in batchIntents" :key="index" class="intent-row">
        <el-input v-model="intent.destination" placeholder="目的地" class="intent-destination" />
        <el-date-picker
          v-model="intent.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="intent-dates"
        />
        <span class="intent-label">人数</span>
        <el-input-number v-model="intent.adults" :min="1" :max="20" class="intent-adults" />
        <el-button
          v-if="batchIntents.length > 1"
          text
          type="danger"
          @click="removeIntent(index)"
        >
          删除
        </el-button>
      </div>
      <el-button size="small" @click="addIntent">添加意图</el-button>

      <div v-if="batchResult.length" class="batch-result">
        <p class="dialog-hint">已提交任务，进度请到攻略详情页查看：</p>
        <div v-for="task in batchResult" :key="task.taskId" class="task-row">
          <span>任务 {{ task.taskId }}</span>
          <el-tag size="small" :type="task.status === 'failed' ? 'danger' : 'success'">
            {{ task.status === 'failed' ? '提交失败' : '生成中' }}
          </el-tag>
          <el-button size="small" link @click="openTask(task.taskId)">查看进度</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="batchVisible = false">关闭</el-button>
        <el-button type="primary" @click="submitBatch">提交生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publishVisible" title="发布新攻略" width="560px">
      <el-form label-width="90px">
        <el-form-item label="攻略" required>
          <el-select
            v-model="publishForm.guidebookId"
            placeholder="选择要发布的攻略"
            @change="onGuidebookChange"
          >
            <el-option
              v-for="item in publishable"
              :key="item.guidebookId"
              :label="item.title || `攻略 ${item.guidebookId}`"
              :value="item.guidebookId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="publishForm.title" maxlength="120" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="publishForm.description" type="textarea" maxlength="500" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="publishForm.coverUrl" maxlength="500" />
        </el-form-item>
        <el-form-item label="价格（元）">
          <el-input-number v-model="publishForm.priceYuan" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPublish">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.expert-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-head h2 {
  margin: 0;
  font-size: 20px;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-3);
}

.muted {
  color: var(--text-3);
}

.reject-reason {
  color: var(--danger-600, #d03050);
}

.earnings-alert {
  margin-bottom: var(--space-4);
}

.section-title {
  margin: var(--space-5) 0 var(--space-3);
}

.dialog-hint {
  color: var(--text-2);
  font-size: 13px;
  margin: 0 0 var(--space-3);
}

.token-box {
  margin-top: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle, #f5f7fa);
}

.token-text {
  display: block;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}

.intent-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.intent-destination {
  width: 160px;
}

.intent-dates {
  flex: 1;
}

.intent-label {
  color: var(--text-secondary, #606266);
  font-size: 13px;
  white-space: nowrap;
}

.intent-adults {
  width: 100px;
  flex: none;
}

.batch-result {
  margin-top: var(--space-4);
  border-top: 1px solid var(--border);
  padding-top: var(--space-3);
}

.task-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 4px 0;
}
</style>

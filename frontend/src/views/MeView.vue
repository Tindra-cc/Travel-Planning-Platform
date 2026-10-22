<template>
  <div class="me-page">
    <el-card class="account-card">
      <div class="account-head">
        <div class="avatar">{{ avatarText }}</div>
        <div class="account-info">
          <div class="nickname">{{ user?.nickname ?? '旅行者' }}</div>
          <div class="account-line">
            <span>{{ user?.account ?? '-' }}</span>
            <el-tag v-if="user" size="small" effect="plain">{{ user.userLevel }}</el-tag>
          </div>
        </div>
      </div>

      <div class="quota">
        <div class="quota-head">
          <span>今日生成配额</span>
          <span class="muted">{{ used }} / {{ quota }}</span>
        </div>
        <el-progress :percentage="quotaPercent" :stroke-width="8" :show-text="false" />
      </div>

      <el-alert
        v-if="user && !user.profileCompleted"
        type="warning"
        title="画像未完善"
        description="补全预算、节奏和兴趣偏好后，生成的攻略会更贴合你。"
        show-icon
        :closable="false"
        class="profile-alert"
      />

      <div class="quick-entries">
        <div class="entry" @click="router.push('/profile')">
          <span class="entry-label">完善画像</span>
          <span class="entry-desc">预算 / 节奏 / 兴趣</span>
        </div>
        <div v-if="user?.expertEligible" class="entry" @click="router.push('/expert')">
          <span class="entry-label">达人工作台</span>
          <span class="entry-desc">客户授权 / 批量生成</span>
        </div>
        <div class="entry" @click="router.push('/chat')">
          <span class="entry-label">去对话</span>
          <span class="entry-desc">描述需求，生成新攻略</span>
        </div>
      </div>

      <el-button class="logout-btn" plain type="danger" @click="onLogout">退出登录</el-button>
    </el-card>

    <div class="right-col">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>我的攻略</span>
            <el-button size="small" text @click="router.push('/guidebook')">全部</el-button>
          </div>
        </template>
        <el-skeleton v-if="guidebookLoading" :rows="3" animated />
        <ul v-else-if="guidebooks.length" class="mini-list">
          <li
            v-for="item in guidebooks"
            :key="item.guidebookId"
            @click="router.push(`/guidebook/${item.guidebookId}`)"
          >
            <span class="mini-title">{{ item.title || '未命名攻略' }}</span>
            <el-tag size="small" :type="statusType(item.status)">{{ statusLabel(item.status) }}</el-tag>
          </li>
        </ul>
        <el-empty v-else description="还没有攻略" :image-size="72" />
      </el-card>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>我的对话</span>
            <el-button size="small" text @click="router.push('/chat')">全部</el-button>
          </div>
        </template>
        <el-skeleton v-if="sessionLoading" :rows="3" animated />
        <ul v-else-if="sessions.length" class="mini-list">
          <li
            v-for="item in sessions"
            :key="item.sessionId"
            @click="router.push({ path: '/chat', query: { sessionId: item.sessionId } })"
          >
            <span class="mini-title">{{ item.title }}</span>
            <el-tag size="small" effect="plain">{{ sessionStatusLabel(item.status) }}</el-tag>
          </li>
        </ul>
        <el-empty v-else description="还没有对话" :image-size="72" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as chatApi from '@/api/chat'
import * as guidebookApi from '@/api/guidebook'
import { useAuthStore } from '@/stores/auth'
import type { GuidebookListItem, SessionListItem } from '@/types/api'

const SESSION_STATUS_LABELS: Record<string, string> = {
  collecting: '采集中',
  ready_to_generate: '待生成',
  generating: '生成中',
  generated: '已生成',
  failed: '生成失败'
}
const GUIDEBOOK_STATUS_LABELS: Record<string, string> = {
  generating: '生成中',
  draft: '草稿',
  editing: '编辑中',
  failed: '生成失败',
  confirmed: '已确认',
  executing: '行程中',
  completed: '已完成'
}

const router = useRouter()
const auth = useAuthStore()

const user = computed(() => auth.userInfo)
const guidebooks = ref<GuidebookListItem[]>([])
const sessions = ref<SessionListItem[]>([])
const guidebookLoading = ref(false)
const sessionLoading = ref(false)

const avatarText = computed(() => (user.value?.nickname ?? '旅').slice(0, 1))
const used = computed(() => user.value?.dailyGenerateUsed ?? 0)
const quota = computed(() => user.value?.dailyGenerateQuota ?? 0)
const quotaPercent = computed(() =>
  quota.value > 0 ? Math.min(100, Math.round((used.value / quota.value) * 100)) : 0
)

function statusLabel(status: string) {
  return GUIDEBOOK_STATUS_LABELS[status] ?? status
}
function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'failed') return 'danger'
  if (status === 'generating' || status === 'editing') return 'warning'
  if (status === 'draft') return 'info'
  return 'success'
}
function sessionStatusLabel(status: string) {
  return SESSION_STATUS_LABELS[status] ?? status
}

async function loadGuidebooks() {
  guidebookLoading.value = true
  try {
    const res = await guidebookApi.listGuidebooks(undefined, 1, 5)
    guidebooks.value = res.list
  } catch {
    // 拦截器已提示
  } finally {
    guidebookLoading.value = false
  }
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await chatApi.listSessions(1, 5)
    sessions.value = res.list
  } catch {
    // 拦截器已提示
  } finally {
    sessionLoading.value = false
  }
}

async function onLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}

onMounted(async () => {
  void auth.fetchMe()
  void loadGuidebooks()
  void loadSessions()
})
</script>

<style scoped>
.me-page {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: var(--space-4);
  align-items: flex-start;
}

.account-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--grad-brand);
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  flex-shrink: 0;
}
.account-info {
  min-width: 0;
}
.nickname {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 4px;
}
.account-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 13px;
  color: var(--text-2);
}

.quota {
  margin-top: var(--space-5);
}
.quota-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 6px;
}
.muted {
  color: var(--text-3);
}

.profile-alert {
  margin-top: var(--space-4);
}

.quick-entries {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-5);
}
.entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.entry:hover {
  border-color: var(--brand-500);
  background: var(--brand-050);
}
.entry-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
}
.entry-desc {
  font-size: 12px;
  color: var(--text-3);
}

.logout-btn {
  width: 100%;
  margin-top: var(--space-5);
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mini-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.mini-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 4px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
}
.mini-list li:last-child {
  border-bottom: none;
}
.mini-list li:hover .mini-title {
  color: var(--brand-600);
}
.mini-title {
  font-size: 14px;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 860px) {
  .me-page {
    grid-template-columns: 1fr;
  }
}
</style>

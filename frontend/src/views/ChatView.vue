<template>
  <div class="chat-page">
    <el-card class="session-panel">
      <template #header>
        <div class="card-header">
          <span>会话列表</span>
          <el-button size="small" type="primary" @click="startNew">新建</el-button>
        </div>
      </template>
      <ul class="session-list">
        <li
          v-for="item in sessions"
          :key="item.sessionId"
          :class="{ active: item.sessionId === sessionId }"
          @click="selectSession(item.sessionId)"
        >
          <div class="session-title">{{ item.title }}</div>
          <div class="session-meta">
            <el-tag size="small" :type="statusType(item.status)">{{ statusLabel(item.status) }}</el-tag>
            <el-button size="small" text type="danger" @click.stop="onDelete(item.sessionId)">
              删除
            </el-button>
          </div>
        </li>
        <li v-if="!sessions.length" class="empty">暂无会话</li>
      </ul>
    </el-card>

    <el-card class="chat-panel">
      <template #header>
        <div class="card-header">
          <span>AI 规划对话</span>
        </div>
      </template>

      <div ref="scrollRef" class="chat-box">
        <div v-for="(m, idx) in messages" :key="idx" :class="['bubble-row', m.role]">
          <div class="bubble">{{ m.content }}</div>
        </div>
        <div v-if="!messages.length" class="chat-empty">
          <div class="chat-empty-ico">💬</div>
          <p>说一句你想去哪，比如「国庆想去成都，玩四天」</p>
        </div>

        <div v-if="pendingQuestions.length" class="questions">
          <div class="questions-title">待澄清</div>
          <div class="questions-tags">
            <el-tag v-for="q in pendingQuestions" :key="q.questionId" effect="plain" round>
              {{ q.text }}
            </el-tag>
          </div>
        </div>
      </div>

      <div class="status-bar">
        <el-tag :type="statusType(currentStatus)" effect="light" round>
          {{ statusLabel(currentStatus) }}
        </el-tag>
        <el-tag v-if="adjustMode" type="warning" effect="dark" round>攻略调整模式</el-tag>
        <el-button
          v-if="currentStatus === 'ready_to_generate'"
          type="success"
          size="small"
          :loading="confirming"
          @click="onConfirm"
        >
          确认生成攻略
        </el-button>
        <el-button
          v-if="canAdjust"
          size="small"
          :type="adjustMode ? 'warning' : 'primary'"
          :plain="adjustMode"
          @click="toggleAdjustMode"
        >
          {{ adjustMode ? '退出调整' : '调整当前攻略' }}
        </el-button>
        <el-button v-if="sessionGuidebookId" size="small" text @click="goGuidebook">
          查看攻略
        </el-button>
      </div>

      <div class="input-row">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          :disabled="currentStatus === 'generating'"
          :placeholder="
            adjustMode
              ? '描述想怎么调整攻略，例如：Day2 太赶了，把博物馆挪到第三天'
              : '描述你的旅行需求，回车发送'
          "
          @keyup.enter.exact="onSend"
        />
        <el-button
          type="primary"
          :loading="sending || adjusting"
          :disabled="currentStatus === 'generating'"
          @click="onSend"
        >
          {{ adjustMode ? '执行调整' : '发送' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as chatApi from '@/api/chat'
import * as guidebookApi from '@/api/guidebook'
import { openChatSocket, type MessageDelta } from '@/utils/ws'
import type { ChatMessage, PendingQuestion, SessionListItem } from '@/types/api'

const route = useRoute()
const router = useRouter()

const STATUS_LABELS: Record<string, string> = {
  collecting: '信息采集中',
  ready_to_generate: '待生成',
  generating: '生成中',
  generated: '已生成',
  failed: '生成失败'
}
function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}
function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'ready_to_generate' || status === 'generated') return 'success'
  if (status === 'generating') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}

const sessions = ref<SessionListItem[]>([])
const sessionId = ref<string | null>(null)
const currentStatus = ref('')
const messages = ref<ChatMessage[]>([])
const pendingQuestions = ref<PendingQuestion[]>([])
const input = ref('')
const sending = ref(false)
const confirming = ref(false)
const scrollRef = ref<HTMLElement>()

// 攻略调整是独立的显式模式：请求走 HTTP，结果只做本地展示，不写入会话消息表
const sessionGuidebookId = ref<string | null>(null)
const guidebookStatus = ref('')
const adjustMode = ref(false)
const adjusting = ref(false)
const canAdjust = computed(
  () =>
    !!sessionGuidebookId.value &&
    ['draft', 'editing'].includes(guidebookStatus.value) &&
    currentStatus.value !== 'generating'
)

let socket: WebSocket | null = null

async function loadSessions() {
  const res = await chatApi.listSessions(1, 50)
  sessions.value = res.list
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

function startNew() {
  closeSocket()
  sessionId.value = null
  currentStatus.value = ''
  messages.value = []
  pendingQuestions.value = []
  input.value = ''
  sessionGuidebookId.value = null
  guidebookStatus.value = ''
  adjustMode.value = false
}

async function selectSession(id: string) {
  closeSocket()
  try {
    const detail = await chatApi.getSession(id)
    sessionId.value = id
    currentStatus.value = detail.status
    messages.value = detail.messages
    pendingQuestions.value = detail.pendingQuestions ?? []
    sessionGuidebookId.value = detail.guidebookId ?? null
    guidebookStatus.value = ''
    adjustMode.value = false
    if (sessionGuidebookId.value) {
      void loadGuidebookStatus(sessionGuidebookId.value)
    }
    scrollToBottom()
  } catch {
    // 拦截器已提示
  }
}

async function loadGuidebookStatus(id: string) {
  try {
    const guidebook = await guidebookApi.getGuidebook(id)
    if (sessionGuidebookId.value === id) {
      guidebookStatus.value = guidebook.status
    }
  } catch {
    // 攻略不可读时隐藏调整入口
  }
}

function toggleAdjustMode() {
  adjustMode.value = !adjustMode.value
  input.value = ''
}

function goGuidebook() {
  if (!sessionGuidebookId.value) return
  router.push({
    name: 'guidebook-detail',
    params: { guidebookId: sessionGuidebookId.value },
    query: sessionId.value ? { sessionId: sessionId.value } : {}
  })
}

// 从推荐卡片跳转过来时，用目的地/时长预填首句；从攻略页带 sessionId 回来时直接打开该会话
function applyContextFromQuery() {
  const { destination, title, durationDays, sessionId: sid } = route.query
  if (typeof sid === 'string' && sid) {
    void selectSession(sid)
    return
  }
  if (typeof destination === 'string' && destination) {
    const days = typeof durationDays === 'string' ? Number(durationDays) : NaN
    input.value =
      Number.isFinite(days) && days > 0
        ? `我想去${destination}玩${days}天`
        : `我想去${destination}旅行`
  } else if (typeof title === 'string' && title) {
    input.value = `我想参考「${title}」安排一次旅行`
  }
}

async function onSend() {
  const content = input.value.trim()
  if (!content) return
  if (adjustMode.value) {
    await sendAdjust(content)
    return
  }
  sending.value = true
  try {
    if (!sessionId.value) {
      await createAndSend(content)
    } else {
      await sendViaSocket(content)
    }
    input.value = ''
  } catch {
    // 拦截器已提示
  } finally {
    sending.value = false
  }
}

async function sendAdjust(content: string) {
  const guidebookId = sessionGuidebookId.value
  if (!guidebookId) return
  adjusting.value = true
  try {
    const res = await guidebookApi.adjustGuidebook(guidebookId, content)
    messages.value.push({
      messageId: `local-adjust-user-${Date.now()}`,
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    })
    messages.value.push({
      messageId: `local-adjust-${res.version}`,
      role: 'assistant',
      content: `已按你的要求调整攻略（v${res.version}）：${res.changeSummary}`,
      createdAt: new Date().toISOString()
    })
    input.value = ''
    guidebookStatus.value = 'editing'
    if (res.conflicts?.length) {
      ElMessage.warning(`调整完成，但仍有 ${res.conflicts.length} 处冲突需要处理`)
    } else {
      ElMessage.success('攻略已更新')
    }
    scrollToBottom()
  } catch {
    // 拦截器已提示
  } finally {
    adjusting.value = false
  }
}

async function createAndSend(content: string) {
  const res = await chatApi.createSession(content)
  sessionId.value = res.sessionId
  currentStatus.value = res.status
  messages.value = res.messages
  pendingQuestions.value = res.pendingQuestions ?? []
  scrollToBottom()
  await loadSessions()
}

function sendViaSocket(content: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const id = sessionId.value!
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      socket = openChatSocket(id, {
        onDelta: handleDelta,
        onDone: handleDone,
        onError: (e) => {
          ElMessage.error(e.message)
          reject(new Error(e.message))
        },
        onClose: () => {
          socket = null
        }
      })
      socket.onopen = () => {
        messages.value.push({
          messageId: 'pending',
          role: 'user',
          content,
          createdAt: new Date().toISOString()
        })
        scrollToBottom()
        socket!.send(JSON.stringify({ content }))
        resolve()
      }
      socket.onerror = () => reject(new Error('连接失败'))
    } else {
      messages.value.push({
        messageId: 'pending',
        role: 'user',
        content,
        createdAt: new Date().toISOString()
      })
      scrollToBottom()
      socket.send(JSON.stringify({ content }))
      resolve()
    }
  })
}

function handleDelta(ev: MessageDelta) {
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'assistant' || last.messageId !== ev.messageId) {
    messages.value.push({
      messageId: ev.messageId,
      role: 'assistant',
      content: ev.delta,
      createdAt: new Date().toISOString()
    })
  } else {
    last.content += ev.delta
  }
  scrollToBottom()
}

function handleDone(ev: { sessionStatus: string; pendingQuestions: PendingQuestion[] }) {
  currentStatus.value = ev.sessionStatus
  pendingQuestions.value = ev.pendingQuestions ?? []
  loadSessions()
}

async function onConfirm() {
  if (!sessionId.value) return
  confirming.value = true
  try {
    const res = await chatApi.confirmSession(sessionId.value)
    currentStatus.value = res.status
    if (res.guidebookId) {
      ElMessage.success('已触发生成，正在为你规划攻略')
      await loadSessions()
      router.push({
        name: 'guidebook-detail',
        params: { guidebookId: res.guidebookId },
        query: { sessionId: sessionId.value }
      })
    }
  } catch {
    // 拦截器已提示
  } finally {
    confirming.value = false
  }
}

async function onDelete(id: string) {
  try {
    await ElMessageBox.confirm('确认删除该会话？', '提示', { type: 'warning' })
  } catch {
    return // 用户取消
  }
  await chatApi.deleteSession(id)
  if (id === sessionId.value) startNew()
  await loadSessions()
}

function closeSocket() {
  if (socket) {
    socket.close()
    socket = null
  }
}

onMounted(async () => {
  await loadSessions()
  applyContextFromQuery()
})
onBeforeUnmount(closeSocket)
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}

.session-panel {
  width: 268px;
  flex-shrink: 0;
}
.chat-panel {
  flex: 1;
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
}

.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.session-list li {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}
.session-list li:hover {
  background: var(--bg-page);
}
.session-list li.active {
  background: var(--brand-050);
}
.session-list li.active .session-title {
  color: var(--brand-600);
  font-weight: 600;
}
.session-title {
  font-size: 14px;
  margin-bottom: 6px;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.session-list .empty {
  color: var(--text-3);
  text-align: center;
  cursor: default;
  font-size: 13px;
  padding: var(--space-6) 0;
}
.session-list .empty:hover {
  background: none;
}

.chat-box {
  height: 440px;
  overflow-y: auto;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-page);
}
.bubble-row {
  display: flex;
  margin-bottom: var(--space-3);
}
.bubble-row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text-1);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble-row.user .bubble {
  background: var(--grad-brand);
  border: none;
  color: #fff;
  box-shadow: 0 6px 16px rgba(18, 179, 168, 0.22);
}

.chat-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: var(--text-3);
  font-size: 14px;
}
.chat-empty-ico {
  font-size: 40px;
  opacity: 0.7;
}
.chat-empty p {
  margin: 0;
}

.questions {
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--border);
}
.questions-title {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: var(--space-2);
  font-weight: 500;
}
.questions-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.status-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin: var(--space-4) 0;
}
.input-row {
  display: flex;
  gap: var(--space-2);
  align-items: flex-end;
}
</style>

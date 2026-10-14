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

    <el-card class="chat-panel" header="AI 规划对话">
      <div ref="scrollRef" class="chat-box">
        <div v-for="(m, idx) in messages" :key="idx" :class="['bubble-row', m.role]">
          <div class="bubble">
            <div>{{ m.content }}</div>
          </div>
        </div>
        <div v-if="pendingQuestions.length" class="questions">
          <div class="questions-title">待澄清：</div>
          <el-tag v-for="q in pendingQuestions" :key="q.questionId" class="question-tag">
            {{ q.text }}
          </el-tag>
        </div>
      </div>

      <div class="status-bar">
        <el-tag :type="statusType(currentStatus)">{{ statusLabel(currentStatus) }}</el-tag>
        <el-button
          v-if="currentStatus === 'ready_to_generate'"
          type="success"
          size="small"
          :loading="confirming"
          @click="onConfirm"
        >
          确认生成攻略
        </el-button>
      </div>

      <div class="input-row">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          :disabled="currentStatus === 'generating'"
          placeholder="描述你的旅行需求，回车发送"
          @keyup.enter.exact="onSend"
        />
        <el-button
          type="primary"
          :loading="sending"
          :disabled="currentStatus === 'generating'"
          @click="onSend"
        >
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as chatApi from '@/api/chat'
import { openChatSocket, type MessageDelta } from '@/utils/ws'
import type { ChatMessage, PendingQuestion, SessionListItem } from '@/types/api'

const STATUS_LABELS: Record<string, string> = {
  collecting: '信息采集中',
  ready_to_generate: '待生成',
  generating: '生成中'
}
function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}
function statusType(status: string): 'success' | 'warning' | 'info' {
  if (status === 'ready_to_generate') return 'success'
  if (status === 'generating') return 'warning'
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
}

async function selectSession(id: string) {
  closeSocket()
  const detail = await chatApi.getSession(id)
  sessionId.value = id
  currentStatus.value = detail.status
  messages.value = detail.messages
  pendingQuestions.value = detail.pendingQuestions ?? []
  scrollToBottom()
}

async function onSend() {
  const content = input.value.trim()
  if (!content) return
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
    ElMessage.success(
      res.guidebookId ? '已触发生成' : '已触发生成（生成流程将在 Sprint 2 接入）'
    )
  } catch {
    // 拦截器已提示
  } finally {
    confirming.value = false
  }
}

async function onDelete(id: string) {
  await ElMessageBox.confirm('确认删除该会话？', '提示', { type: 'warning' })
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

onMounted(loadSessions)
onBeforeUnmount(closeSocket)
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 16px;
}
.session-panel {
  width: 260px;
  flex-shrink: 0;
}
.chat-panel {
  flex: 1;
}
.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.session-list li {
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
}
.session-list li:hover {
  background: #f5f7fa;
}
.session-list li.active {
  background: #ecf5ff;
}
.session-title {
  font-size: 14px;
  margin-bottom: 4px;
}
.session-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.session-list .empty {
  color: #909399;
  text-align: center;
  cursor: default;
}
.chat-box {
  height: 420px;
  overflow-y: auto;
  border: 1px solid #eee;
  border-radius: 4px;
  padding: 10px;
}
.bubble-row {
  display: flex;
  margin-bottom: 10px;
}
.bubble-row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f4f4f5;
  white-space: pre-wrap;
}
.bubble-row.user .bubble {
  background: #409eff;
  color: #fff;
}
.questions {
  margin-top: 12px;
}
.questions-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.question-tag {
  margin-right: 8px;
}
.status-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}
.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

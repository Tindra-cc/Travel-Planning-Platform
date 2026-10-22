import { request } from './request'
import type {
  ConfirmResponse,
  PageResult,
  SendMessageResponse,
  SessionDetail,
  SessionListItem,
  SessionResponse
} from '@/types/api'

export function createSession(firstMessage: string, referencePostId?: string) {
  return request<SessionResponse>({
    url: '/chat/sessions',
    method: 'post',
    data: { firstMessage, referencePostId }
  })
}

export function listSessions(page = 1, pageSize = 20) {
  return request<PageResult<SessionListItem>>({
    url: '/chat/sessions',
    method: 'get',
    params: { page, pageSize }
  })
}

export function getSession(sessionId: string) {
  return request<SessionDetail>({ url: `/chat/sessions/${sessionId}`, method: 'get' })
}

export function sendMessage(sessionId: string, content: string) {
  return request<SendMessageResponse>({
    url: `/chat/sessions/${sessionId}/messages`,
    method: 'post',
    data: { content }
  })
}

export function confirmSession(sessionId: string) {
  return request<ConfirmResponse>({
    url: `/chat/sessions/${sessionId}/confirm`,
    method: 'post'
  })
}

export function deleteSession(sessionId: string) {
  return request<void>({ url: `/chat/sessions/${sessionId}`, method: 'delete' })
}

import { request } from './request'
import type {
  AuthorizeIssued,
  BatchIntent,
  BatchTask,
  EarningsPage,
  ExpertClientAccessLog,
  ExpertClientCreateBody,
  ExpertClientItem,
  ExpertPublicationItem,
  PageResult,
  PublishBody
} from '@/types/api'

/** 页面以元输入，接口与存储统一用分。 */
export function yuanToCents(yuan: number): number {
  return Math.round(yuan * 100)
}

export function listExpertClients(page = 1, pageSize = 20, status?: string) {
  return request<PageResult<ExpertClientItem>>({
    url: '/expert/clients',
    method: 'get',
    params: { page, pageSize, status }
  })
}

export function createExpertClient(body: ExpertClientCreateBody) {
  return request<ExpertClientItem>({
    url: '/expert/clients',
    method: 'post',
    data: body
  })
}

export function getExpertClient(clientId: string) {
  return request<ExpertClientItem>({
    url: `/expert/clients/${clientId}`,
    method: 'get'
  })
}

export function authorizeClient(clientId: string, scope: string[]) {
  return request<AuthorizeIssued>({
    url: `/expert/clients/${clientId}/authorize`,
    method: 'post',
    data: { scope }
  })
}

export function listClientAccessLogs(clientId: string, page = 1, pageSize = 20) {
  return request<PageResult<ExpertClientAccessLog>>({
    url: `/expert/clients/${clientId}/access-logs`,
    method: 'get',
    params: { page, pageSize }
  })
}

export function batchGenerate(clientId: string, intents: BatchIntent[]) {
  return request<{ tasks: BatchTask[] }>({
    url: `/expert/clients/${clientId}/guidebooks/generate`,
    method: 'post',
    data: { intents },
    // 批量提交可能等待生成池检查，放宽到 60 秒
    timeout: 60000
  })
}

export function listPublications(page = 1, pageSize = 20, status?: string) {
  return request<PageResult<ExpertPublicationItem>>({
    url: '/expert/publications',
    method: 'get',
    params: { page, pageSize, status }
  })
}

export function publishGuidebook(guidebookId: string, body: PublishBody) {
  return request<{ publishedId: string; status: string; commissionRate: number }>({
    url: `/expert/guidebooks/${guidebookId}/publish`,
    method: 'post',
    data: body
  })
}

export function offlinePublication(publicationId: string) {
  return request<void>({
    url: `/expert/publications/${publicationId}/offline`,
    method: 'post'
  })
}

export function listEarnings(page = 1, pageSize = 20) {
  return request<EarningsPage>({
    url: '/expert/earnings',
    method: 'get',
    params: { page, pageSize }
  })
}

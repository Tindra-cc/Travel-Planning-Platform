import { request } from './request'
import type {
  GenerateGuidebookResponse,
  GuidebookDetail,
  GuidebookExportResponse,
  GuidebookListItem,
  PageResult
} from '@/types/api'

export interface GenerateGuidebookBody {
  sessionId?: string
  intent?: Record<string, unknown>
  useProfile?: boolean
}

export function generateGuidebook(body: GenerateGuidebookBody) {
  return request<GenerateGuidebookResponse>({
    url: '/guidebooks/generate',
    method: 'post',
    data: body
  })
}

export function getGuidebook(guidebookId: string) {
  return request<GuidebookDetail>({
    url: `/guidebooks/${guidebookId}`,
    method: 'get'
  })
}

export function retryGuidebook(guidebookId: string) {
  return request<GenerateGuidebookResponse>({
    url: `/guidebooks/${guidebookId}/retry`,
    method: 'post'
  })
}

export function listGuidebooks(status?: string, page = 1, pageSize = 20) {
  return request<PageResult<GuidebookListItem>>({
    url: '/guidebooks',
    method: 'get',
    params: { status, page, pageSize }
  })
}

export function deleteGuidebook(guidebookId: string) {
  return request<void>({ url: `/guidebooks/${guidebookId}`, method: 'delete' })
}

export function exportGuidebook(guidebookId: string, format: 'markdown' | 'html') {
  return request<GuidebookExportResponse>({
    url: `/guidebooks/${guidebookId}/export`,
    method: 'post',
    data: { format }
  })
}

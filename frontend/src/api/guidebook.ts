import { request } from './request'
import type {
  GenerateGuidebookResponse,
  GuidebookDetail,
  GuidebookExportResponse,
  GuidebookListItem,
  PageResult,
  GuidebookAdjustResponse,
  GuidebookConfirmResponse,
  GuidebookEditResponse,
  GuidebookValidateResponse,
  GuidebookVersionItem,
  NodePayload
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

export function updateGuidebookNode(guidebookId: string, day: number, nodeId: string, body: NodePayload){
  return request<GuidebookEditResponse>({
    url: `/guidebooks/${guidebookId}/days/${day}/nodes/${nodeId}`,
    method: 'put',
    data: body
  })
}

export function addGuidebookNode(
  guidebookId: string,
  day: number,
  body: NodePayload & { index?: number; type: string }
) {
  return request<GuidebookEditResponse>({
    url: `/guidebooks/${guidebookId}/days/${day}/nodes`,
    method: 'post',
    data: body
  })
}

export function deleteGuidebookNode(guidebookId: string, day: number, nodeId: string) {
  return request<GuidebookEditResponse>({
    url: `/guidebooks/${guidebookId}/days/${day}/nodes/${nodeId}`,
    method: 'delete'
  })
}

export function moveGuidebookNode(
  guidebookId: string,
  nodeId: string,
  body: { targetDay: number; targetIndex: number }
) {
  return request<GuidebookEditResponse>({
    url: `/guidebooks/${guidebookId}/nodes/${nodeId}/move`,
    method: 'post',
    data: body
  })
}

export function validateGuidebook(guidebookId: string) {
  return request<GuidebookValidateResponse>({
    url: `/guidebooks/${guidebookId}/validate`,
    method: 'post'
  })
}

export function confirmGuidebook(guidebookId: string) {
  return request<GuidebookConfirmResponse>({
    url: `/guidebooks/${guidebookId}/confirm`,
    method: 'post'
  })
}

export function listGuidebookVersions(guidebookId: string) {
  return request<GuidebookVersionItem[]>({
    url: `/guidebooks/${guidebookId}/versions`,
    method: 'get'
  })
}

export function restoreGuidebookVersion(guidebookId: string, version: number) {
  return request<GuidebookEditResponse>({
    url: `/guidebooks/${guidebookId}/versions/${version}/restore`,
    method: 'post'
  })
}

export function adjustGuidebook(guidebookId: string, instruction: string) {
  return request<GuidebookAdjustResponse>({
    url: `/guidebooks/${guidebookId}/adjust`,
    method: 'post',
    data: { instruction },
    // AI 调整最坏 120 秒，必须覆盖默认的 30 秒
    timeout: 150000
  })
}

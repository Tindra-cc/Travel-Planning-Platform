import { request } from './request'
import type {
  RegenerateResponse,
  ShareCreateResponse,
  SharedGuidebook
} from '@/types/api'

export function createShare(guidebookId: string, channel: 'link' | 'qrcode') {
  return request<ShareCreateResponse>({
    url: `/guidebooks/${guidebookId}/share`,
    method: 'post',
    data: { channel }
  })
}

export function getSharedGuidebook(shareCode: string) {
  return request<SharedGuidebook>({
    url: `/share/${shareCode}`,
    method: 'get'
  })
}

export function regenerateFromShare(shareCode: string, sessionId: string) {
  return request<RegenerateResponse>({
    url: `/share/${shareCode}/regenerate`,
    method: 'post',
    data: { sessionId }
  })
}

export function revokeShare(guidebookId: string, shareCode: string) {
  return request<void>({
    url: `/guidebooks/${guidebookId}/share/${shareCode}`,
    method: 'delete'
  })
}

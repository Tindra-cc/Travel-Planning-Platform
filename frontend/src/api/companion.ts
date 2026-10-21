import { request } from './request'
import type {
  NodeStatusResult,
  RefreshResult,
  SuggestionDecision,
  TripCompanion
} from '@/types/api'

export function getTripCompanion(guidebookId: string) {
  return request<TripCompanion>({
    url: `/trip-companion/${guidebookId}`,
    method: 'get'
  })
}

export function refreshSuggestions(guidebookId: string) {
  return request<RefreshResult>({
    url: `/trip-companion/${guidebookId}/refresh`,
    method: 'post'
  })
}

export function acceptSuggestion(guidebookId: string, suggestionId: string) {
  return request<SuggestionDecision>({
    url: `/trip-companion/${guidebookId}/suggestions/${suggestionId}/accept`,
    method: 'post'
  })
}

export function rejectSuggestion(guidebookId: string, suggestionId: string) {
  return request<SuggestionDecision>({
    url: `/trip-companion/${guidebookId}/suggestions/${suggestionId}/reject`,
    method: 'post'
  })
}

export function markNodeStatus(guidebookId: string, nodeId: string, status: string) {
  return request<NodeStatusResult>({
    url: `/trip-companion/${guidebookId}/nodes/${nodeId}/status`,
    method: 'post',
    data: { status }
  })
}

import { request } from './request'
import type {
  GuidebookEditResponse,
  LinkValidateResult,
  PageResult,
  ResourceItem
} from '@/types/api'

export interface ResourceSearchParams {
  type: string
  destination: string
  page?: number
  pageSize?: number
}

export function searchResources(params: ResourceSearchParams) {
  return request<PageResult<ResourceItem>>({
    url: '/resources',
    method: 'get',
    params
  })
}

export function bindResource(
  guidebookId: string,
  nodeId: string,
  resourceSlotId: string | null
) {
  return request<GuidebookEditResponse>({
    url: `/guidebooks/${guidebookId}/nodes/${nodeId}/resource`,
    method: 'post',
    data: { resourceSlotId }
  })
}

export function validateLinks(resourceSlotIds: string[]) {
  return request<{ results: LinkValidateResult[] }>({
    url: '/link/validate',
    method: 'post',
    data: { resourceSlotIds }
  })
}

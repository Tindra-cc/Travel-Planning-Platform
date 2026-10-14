import { request } from './request'
import type { GuidePostDetail, GuidePostItem, PageResult } from '@/types/api'

export function recommendGuides(page = 1, pageSize = 12) {
  return request<PageResult<GuidePostItem>>({
    url: '/guides/recommend',
    method: 'get',
    params: { page, pageSize }
  })
}

export function getGuideDetail(postId: string) {
  return request<GuidePostDetail>({ url: `/guides/${postId}`, method: 'get' })
}

import { request } from './request'
import type { ReviewDto, ReviewSubmitBody, ReviewSummary } from '@/types/api'

export function getReviewSummary(guidebookId: string) {
  return request<ReviewSummary>({
    url: `/reviews/${guidebookId}/summary`,
    method: 'get'
  })
}

export function submitReview(body: ReviewSubmitBody) {
  return request<ReviewDto>({
    url: '/reviews',
    method: 'post',
    data: body
  })
}

export function decideProfileIteration(guidebookId: string, accept: boolean) {
  return request<{ profileVersion: number }>({
    url: `/reviews/${guidebookId}/profile-iteration`,
    method: 'post',
    data: { accept }
  })
}

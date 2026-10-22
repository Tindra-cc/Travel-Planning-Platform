import { request } from './request'
import type { CompareResponse, ComparisonDetail, MixPick, MixResponse } from '@/types/api'

export function startComparison(baseGuidebookId: string, variants?: string[]) {
  return request<CompareResponse>({
    url: '/guidebooks/compare',
    method: 'post',
    data: { baseGuidebookId, variants }
  })
}

export function getComparison(comparisonId: string) {
  return request<ComparisonDetail>({
    url: `/guidebooks/compare/${comparisonId}`,
    method: 'get'
  })
}

/** 最近一次对比结果；从未生成过时后端返回 null */
export function getLatestComparison(baseGuidebookId: string) {
  return request<ComparisonDetail | null>({
    url: '/guidebooks/compare/latest',
    method: 'get',
    params: { baseGuidebookId }
  })
}

export function mixComparison(comparisonId: string, picks: MixPick[]) {
  return request<MixResponse>({
    url: `/guidebooks/compare/${comparisonId}/mix`,
    method: 'post',
    data: { picks },
    // 混搭会调用一次 AI，给足超时
    timeout: 150000
  })
}

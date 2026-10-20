import axios from 'axios'
import { request, TOKEN_KEY } from './request'
import type { OfflinePackFormat, OfflinePackStatus, OfflinePackTask } from '@/types/api'

export interface CreateOfflinePackBody {
  format: OfflinePackFormat
  includeMaps: boolean
}

export function createOfflinePack(guidebookId: string, body: CreateOfflinePackBody) {
  return request<OfflinePackTask>({
    url: `/guidebooks/${guidebookId}/offline-pack`,
    method: 'post',
    data: body
  })
}

export function getOfflinePackStatus(taskId: string) {
  return request<OfflinePackStatus>({
    url: `/offline-packs/${taskId}`,
    method: 'get'
  })
}

/**
 * 下载必须是带 token 的请求：直接 <a href> 不会带 Authorization。
 * 这里绕过统一拦截器（响应是文件流而非 Result），错误体自行解析。
 */
export async function downloadOfflinePack(taskId: string, filename: string) {
  const token = localStorage.getItem(TOKEN_KEY)
  let blob: Blob
  try {
    const response = await axios.get<Blob>(`/api/offline-packs/${taskId}/download`, {
      responseType: 'blob',
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    blob = response.data
  } catch (error) {
    throw new Error(await readDownloadError(error))
  }
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

async function readDownloadError(error: unknown): Promise<string> {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    try {
      const body = JSON.parse(await error.response.data.text()) as { message?: string }
      if (body.message) {
        return body.message
      }
    } catch {
      // 非 JSON 错误体，退回通用文案
    }
  }
  return '下载失败，请稍后重试'
}

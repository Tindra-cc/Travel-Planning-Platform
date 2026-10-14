import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/api'

export const TOKEN_KEY = 'travel_token'

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as Result
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      handleBusinessError(body.code, body.message)
      return Promise.reject(new Error(body.message))
    }
    return response.data
  },
  (error) => {
    const body = error.response?.data as Result | undefined
    if (body && typeof body.code === 'number') {
      handleBusinessError(body.code, body.message)
      return Promise.reject(new Error(body.message))
    }
    ElMessage.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

function handleBusinessError(code: number, message: string) {
  if (code === 40100 || code === 40101) {
    localStorage.removeItem(TOKEN_KEY)
    ElMessage.error(message || '登录已失效，请重新登录')
    import('@/router').then(({ default: router }) => {
      const current = router.currentRoute.value
      if (current.name !== 'login') {
        router.push({ name: 'login', query: { redirect: current.fullPath } })
      }
    })
    return
  }
  ElMessage.error(message || '请求失败')
}

/** 统一请求：成功时直接返回 data（已由拦截器剥离 Result 外壳）。 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config) as Promise<T>
}

export default instance

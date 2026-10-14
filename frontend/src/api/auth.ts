import { request } from './request'
import type {
  AuthResponse,
  CurrentUser,
  LoginRequest,
  RegisterRequest,
  VerifyCodeRequest,
  VerifyCodeResponse
} from '@/types/api'

export function sendVerifyCode(data: VerifyCodeRequest) {
  return request<VerifyCodeResponse>({ url: '/auth/verify-code', method: 'post', data })
}

export function register(data: RegisterRequest) {
  return request<AuthResponse>({ url: '/auth/register', method: 'post', data })
}

export function login(data: LoginRequest) {
  return request<AuthResponse>({ url: '/auth/login', method: 'post', data })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

export function fetchMe() {
  return request<CurrentUser>({ url: '/auth/me', method: 'get' })
}

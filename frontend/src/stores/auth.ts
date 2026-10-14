import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { TOKEN_KEY } from '@/api/request'
import type { CurrentUser, LoginRequest, RegisterRequest } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const userInfo = ref<CurrentUser | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setToken(value: string | null) {
    token.value = value
    if (value) {
      localStorage.setItem(TOKEN_KEY, value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  async function login(payload: LoginRequest) {
    const res = await authApi.login(payload)
    setToken(res.token)
    await fetchMe()
    return res
  }

  async function register(payload: RegisterRequest) {
    const res = await authApi.register(payload)
    setToken(res.token)
    await fetchMe()
    return res
  }

  async function fetchMe() {
    if (!token.value) return null
    userInfo.value = await authApi.fetchMe()
    return userInfo.value
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      setToken(null)
      userInfo.value = null
    }
  }

  return { token, userInfo, isLoggedIn, login, register, fetchMe, logout, setToken }
})

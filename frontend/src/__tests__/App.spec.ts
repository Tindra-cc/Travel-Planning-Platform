import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import App from '../App.vue'
import { useAuthStore } from '@/stores/auth'
import { TOKEN_KEY } from '@/api/request'
import type { CurrentUser } from '@/types/api'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }]
})

function user(expertEligible: boolean): CurrentUser {
  return {
    userId: '7',
    account: 'expert@example.com',
    accountType: 'email',
    nickname: expertEligible ? '达人甲' : '普通用户',
    userLevel: 'L3',
    profileCompleted: true,
    dailyGenerateQuota: 10,
    dailyGenerateUsed: 0,
    expertEligible
  }
}

function mountApp(expertEligible: boolean | null) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (expertEligible !== null) {
    localStorage.setItem(TOKEN_KEY, 'token')
    useAuthStore().userInfo = user(expertEligible)
  }
  return mount(App, {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: { RouterView: true }
    }
  })
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('未登录时展示登录入口', async () => {
    const wrapper = mountApp(null)
    expect(wrapper.text()).toContain('登录')
    wrapper.unmount()
  })

  it('达人才显示达人导航入口', async () => {
    const wrapper = mountApp(true)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('达人')
    wrapper.unmount()
  })

  it('非达人不显示达人导航入口', async () => {
    const wrapper = mountApp(false)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).not.toContain('达人')
    wrapper.unmount()
  })
})

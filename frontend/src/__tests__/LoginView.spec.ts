import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import LoginView from '@/views/LoginView.vue'
import * as authApi from '@/api/auth'
import { ApiError } from '@/api/request'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
  sendVerifyCode: vi.fn()
}))

const mockedLogin = vi.mocked(authApi.login)
const mockedFetchMe = vi.mocked(authApi.fetchMe)

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/discover', name: 'discover', component: { template: '<div>discover</div>' } }
    ]
  })
  await router.push('/login')
  await router.isReady()
  const wrapper = mount(LoginView, {
    global: { plugins: [createPinia(), router, ElementPlus] }
  })
  await flushPromises()
  return { wrapper, router }
}

async function fill(wrapper: ReturnType<typeof mount>, account: string, password: string) {
  await wrapper.find('input[placeholder="邮箱或手机号"]').setValue(account)
  await wrapper.find('input[type="password"]').setValue(password)
}

describe('LoginView', () => {
  beforeEach(() => {
    mockedLogin.mockReset()
    mockedFetchMe.mockReset()
    localStorage.clear()
  })

  it('邮箱格式错误时就地提示且不发起登录', async () => {
    const { wrapper } = await mountView()
    await fill(wrapper, 'not-an-email', 'password1')

    await wrapper.find('.submit-btn').trigger('click')
    // Element Plus 错误文案经 100ms 防抖后渲染
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('邮箱格式不正确')
    })

    expect(mockedLogin).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('切换到注册模式展示验证码输入', async () => {
    const { wrapper } = await mountView()

    const toggle = wrapper.findAll('button').find((b) => b.text() === '注册')
    expect(toggle).toBeTruthy()
    await toggle!.trigger('click')

    expect(wrapper.find('input[placeholder="6 位验证码"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('注册并登录')
    wrapper.unmount()
  })

  it('登录成功后跳转发现页', async () => {
    mockedLogin.mockResolvedValue({
      userId: '1',
      token: 'token-1',
      userLevel: 'L1',
      profileCompleted: false
    })
    mockedFetchMe.mockResolvedValue({
      userId: '1',
      nickname: '旅行者',
      userLevel: 'L1',
      profileCompleted: false,
      dailyGenerateQuota: 3,
      dailyGenerateUsed: 0
    })
    const { wrapper, router } = await mountView()
    await fill(wrapper, 'a@b.com', 'password1')

    await wrapper.find('.submit-btn').trigger('click')
    await flushPromises()

    expect(mockedLogin).toHaveBeenCalledWith({ account: 'a@b.com', password: 'password1' })
    expect(router.currentRoute.value.path).toBe('/discover')
    wrapper.unmount()
  })

  it('42300 时启动锁定倒计时并禁用提交', async () => {
    mockedLogin.mockRejectedValue(new ApiError(42300, '账号已锁定'))
    const { wrapper } = await mountView()
    await fill(wrapper, 'a@b.com', 'password1')

    await wrapper.find('.submit-btn').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('账号已锁定，请 30:00 后重试')
    expect(wrapper.find('.submit-btn').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import ExpertView from '@/views/ExpertView.vue'
import * as expertApi from '@/api/expert'
import { ApiError } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser, ExpertClientItem } from '@/types/api'

vi.mock('@/api/expert', () => ({
  listExpertClients: vi.fn(),
  createExpertClient: vi.fn(),
  getExpertClient: vi.fn(),
  authorizeClient: vi.fn(),
  listClientAccessLogs: vi.fn(),
  batchGenerate: vi.fn(),
  listPublications: vi.fn(),
  publishGuidebook: vi.fn(),
  offlinePublication: vi.fn(),
  listEarnings: vi.fn()
}))

vi.mock('@/api/guidebook', () => ({
  listGuidebooks: vi.fn()
}))

const mockedClients = vi.mocked(expertApi.listExpertClients)
const mockedCreateClient = vi.mocked(expertApi.createExpertClient)
const mockedAuthorize = vi.mocked(expertApi.authorizeClient)
const mockedBatch = vi.mocked(expertApi.batchGenerate)
const mockedEarnings = vi.mocked(expertApi.listEarnings)

function client(overrides: Partial<ExpertClientItem> = {}): ExpertClientItem {
  return {
    clientId: '11',
    name: '张女士',
    contact: '13800000000',
    note: null,
    linkedUserId: null,
    status: 'active',
    createdAt: '2026-10-01 10:00:00',
    authorizationStatus: 'none',
    authorizedScopes: null,
    authorizedAt: null,
    ...overrides
  }
}

async function mountExpert(eligible: boolean) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  const user: CurrentUser = {
    userId: '7',
    account: 'expert@example.com',
    accountType: 'email',
    nickname: '达人甲',
    userLevel: 'L3',
    profileCompleted: true,
    dailyGenerateQuota: 10,
    dailyGenerateUsed: 0,
    expertEligible: eligible
  }
  auth.userInfo = user

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/expert', component: ExpertView }]
  })
  await router.push('/expert')
  await router.isReady()

  const wrapper = mount(ExpertView, {
    global: { plugins: [pinia, router, ElementPlus], stubs: { teleport: true } }
  })
  await flushPromises()
  return { wrapper, router }
}

function findButton(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedClients.mockResolvedValue({ list: [], page: 1, pageSize: 20, total: 0 })
  mockedEarnings.mockResolvedValue({
    list: [],
    page: 1,
    pageSize: 20,
    total: 0,
    settlementAvailable: false
  })
})

describe('ExpertView', () => {
  it('非达人显示引导页且不请求客户列表', async () => {
    const { wrapper } = await mountExpert(false)

    expect(wrapper.text()).toContain('需要达人权限')
    expect(mockedClients).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('客户列表返回 40300 时显示引导页', async () => {
    mockedClients.mockRejectedValue(new ApiError(40300, '需要达人权限'))

    const { wrapper } = await mountExpert(true)

    expect(wrapper.text()).toContain('需要达人权限')
    wrapper.unmount()
  })

  it('达人进入后展示客户', async () => {
    mockedClients.mockResolvedValue({
      list: [client()],
      page: 1,
      pageSize: 20,
      total: 1
    })

    const { wrapper } = await mountExpert(true)

    expect(mockedClients).toHaveBeenCalledWith(1, 20)
    expect(wrapper.text()).toContain('张女士')
    wrapper.unmount()
  })

  it('建档提交裁剪后的字段并新增一行', async () => {
    mockedClients.mockResolvedValue({ list: [], page: 1, pageSize: 20, total: 0 })
    mockedCreateClient.mockResolvedValue(client({ clientId: '12', name: '李先生' }))

    const { wrapper } = await mountExpert(true)
    await findButton(wrapper, '新建客户')!.trigger('click')
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue(' 李先生 ')
    await inputs[1]!.setValue(' 13900000000 ')
    await inputs[2]!.setValue(' ')
    await findButton(wrapper, '创建')!.trigger('click')
    await flushPromises()

    expect(mockedCreateClient).toHaveBeenCalledWith({
      name: '李先生',
      contact: '13900000000',
      note: null,
      linkedUserId: null
    })
    expect(wrapper.text()).toContain('李先生')
    wrapper.unmount()
  })

  it('授权令牌只在弹窗打开时可见，关闭后清空', async () => {
    mockedClients.mockResolvedValue({ list: [client()], page: 1, pageSize: 20, total: 1 })
    mockedAuthorize.mockResolvedValue({
      authorizationId: '9',
      confirmToken: 'once-only-token',
      expiresAt: '2026-10-08 10:00:00',
      scope: ['profile', 'guidebooks']
    })

    const { wrapper } = await mountExpert(true)
    await findButton(wrapper, '发起授权')!.trigger('click')
    await flushPromises()
    await findButton(wrapper, '生成令牌')!.trigger('click')
    await flushPromises()

    expect(mockedAuthorize).toHaveBeenCalledWith('11', ['profile', 'guidebooks'])
    expect(wrapper.find('.token-text').text()).toBe('once-only-token')

    await findButton(wrapper, '我已转交')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.token-text').exists()).toBe(false)

    await findButton(wrapper, '发起授权')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.token-text').exists()).toBe(false)
    wrapper.unmount()
  })

  it('批量生成最多 5 条，未补全时不调用接口', async () => {
    mockedClients.mockResolvedValue({
      list: [client({ authorizationStatus: 'active' })],
      page: 1,
      pageSize: 20,
      total: 1
    })

    const { wrapper } = await mountExpert(true)
    await findButton(wrapper, '批量生成')!.trigger('click')
    await flushPromises()

    for (let i = 0; i < 5; i++) {
      await findButton(wrapper, '添加意图')!.trigger('click')
      await flushPromises()
    }
    expect(wrapper.findAll('.intent-row')).toHaveLength(5)

    await findButton(wrapper, '提交生成')!.trigger('click')
    await flushPromises()
    expect(mockedBatch).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('收益为占位时显示声明与空状态', async () => {
    mockedClients.mockResolvedValue({ list: [client()], page: 1, pageSize: 20, total: 1 })

    const { wrapper } = await mountExpert(true)
    await wrapper.findAll('.el-tabs__item').find((tab) => tab.text() === '收益')!.trigger('click')
    await flushPromises()

    expect(mockedEarnings).toHaveBeenCalledWith(1, 20)
    expect(wrapper.text()).toContain('收益为占位展示')
    expect(wrapper.text()).toContain('暂无收益记录')
    wrapper.unmount()
  })
})

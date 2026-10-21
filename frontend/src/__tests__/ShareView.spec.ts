import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import ShareView from '@/views/ShareView.vue'
import * as shareApi from '@/api/share'
import * as chatApi from '@/api/chat'
import { ApiError, TOKEN_KEY } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import type { SharedGuidebook } from '@/types/api'

vi.mock('@/api/share', () => ({
  createShare: vi.fn(),
  revokeShare: vi.fn(),
  getSharedGuidebook: vi.fn(),
  regenerateFromShare: vi.fn()
}))

vi.mock('@/api/chat', () => ({
  listSessions: vi.fn()
}))

const mockedGet = vi.mocked(shareApi.getSharedGuidebook)
const mockedRegenerate = vi.mocked(shareApi.regenerateFromShare)
const mockedListSessions = vi.mocked(chatApi.listSessions)

const Empty = { render: () => null }

async function mountView(loggedIn: boolean) {
  localStorage.removeItem(TOKEN_KEY)
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  if (loggedIn) auth.setToken('test-token')

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/share/:shareCode', name: 'share', component: ShareView },
      { path: '/login', name: 'login', component: Empty },
      { path: '/guidebook/:guidebookId', name: 'guidebook-detail', component: Empty },
      { path: '/chat', name: 'chat', component: Empty }
    ]
  })
  await router.push('/share/abc123')
  await router.isReady()

  const wrapper = mount(ShareView, {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: { teleport: true }
    }
  })
  await flushPromises()
  return { wrapper, router }
}

function shared(overrides: Partial<SharedGuidebook> = {}): SharedGuidebook {
  return {
    shareCode: 'abc123',
    title: '东京 5 日',
    guidebookVersion: 2,
    sharedAt: '2026-10-20T09:00:00',
    expiresAt: null,
    days: [
      {
        day: 1,
        nodes: [
          {
            nodeId: 'n1',
            timeSlot: { start: '09:00', end: '11:00' },
            type: 'activity',
            place: { name: '浅草寺' },
            source: null,
            dataFreshness: '2026-10-18T09:00:00',
            note: '早点去',
            openingHours: '09:00-17:00'
          }
        ]
      }
    ],
    budgetBreakdown: { currency: 'CNY', items: null, total: { min: 3000, max: 5000 } },
    packingList: ['雨具'],
    culturalTips: ['茶馆慢坐'],
    pitfallTips: ['避开周末'],
    disclaimer: '内容仅供参考',
    degradedMode: false,
    travelDataAt: '2026-10-19T08:00:00',
    ...overrides
  }
}

function button(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(text))
}

describe('ShareView', () => {
  beforeEach(() => {
    mockedGet.mockReset()
    mockedRegenerate.mockReset()
    mockedListSessions.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.removeItem(TOKEN_KEY)
  })

  it('公开加载渲染标题、日程、预算与贴士', async () => {
    mockedGet.mockResolvedValue(shared())

    const { wrapper } = await mountView(false)

    expect(wrapper.text()).toContain('东京 5 日')
    expect(wrapper.text()).toContain('只读分享')
    expect(wrapper.text()).toContain('浅草寺')
    expect(wrapper.text()).toContain('09:00 - 11:00')
    expect(wrapper.text()).toContain('3000')
    expect(wrapper.text()).toContain('茶馆慢坐')
    expect(wrapper.text()).toContain('避开周末')
    expect(wrapper.text()).toContain('内容仅供参考')
    wrapper.unmount()
  })

  it('40400 显示分享不存在', async () => {
    mockedGet.mockRejectedValue(new ApiError(40400, '分享不存在或已删除'))

    const { wrapper } = await mountView(false)

    expect(wrapper.text()).toContain('分享不存在')
    expect(wrapper.text()).toContain('分享不存在或已删除')
    wrapper.unmount()
  })

  it('40900 显示分享不可用', async () => {
    mockedGet.mockRejectedValue(new ApiError(40900, '分享已撤回'))

    const { wrapper } = await mountView(false)

    expect(wrapper.text()).toContain('分享不可用')
    expect(wrapper.text()).toContain('分享已撤回')
    wrapper.unmount()
  })

  it('降级分享显示数据缺失提示', async () => {
    mockedGet.mockResolvedValue(shared({ degradedMode: true }))

    const { wrapper } = await mountView(false)

    expect(wrapper.text()).toContain('部分实时数据缺失')
    wrapper.unmount()
  })

  it('未登录点击再生成跳转登录并保留完整路径', async () => {
    mockedGet.mockResolvedValue(shared())

    const { wrapper, router } = await mountView(false)
    await button(wrapper, '为我再生成')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/share/abc123')
    expect(mockedListSessions).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已登录但没有可用会话时显示空态', async () => {
    mockedGet.mockResolvedValue(shared())
    mockedListSessions.mockResolvedValue({ list: [], page: 1, pageSize: 50, total: 0 })

    const { wrapper } = await mountView(true)
    await button(wrapper, '为我再生成')!.trigger('click')
    await flushPromises()

    expect(mockedListSessions).toHaveBeenCalledWith(1, 50)
    expect(wrapper.text()).toContain('没有可用的会话')
    expect(wrapper.text()).toContain('去创建一个')
    wrapper.unmount()
  })

  it('选择会话提交后展示时效差异并跳转新攻略', async () => {
    mockedGet.mockResolvedValue(shared())
    mockedListSessions.mockResolvedValue({
      list: [
        {
          sessionId: 's1',
          title: '东京之旅',
          status: 'ready_to_generate',
          updatedAt: '2026-10-20 10:00'
        },
        { sessionId: 's2', title: '收集中', status: 'collecting', updatedAt: '2026-10-20 11:00' }
      ],
      page: 1,
      pageSize: 50,
      total: 2
    })
    mockedRegenerate.mockResolvedValue({
      guidebookId: '88',
      status: 'generating',
      dataFreshnessDiff: [
        { nodeId: 'n1', old: '2026-10-18T09:00:00', new: 'pending_refresh' }
      ]
    })

    const { wrapper, router } = await mountView(true)
    await button(wrapper, '为我再生成')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('东京之旅')
    expect(wrapper.text()).not.toContain('收集中')

    await wrapper.find('.session-list input[type="radio"]').setValue(true)
    await button(wrapper, '开始生成')!.trigger('click')
    await flushPromises()

    expect(mockedRegenerate).toHaveBeenCalledWith('abc123', 's1')
    expect(wrapper.text()).toContain('n1')
    expect(wrapper.text()).toContain('待刷新')

    await button(wrapper, '查看生成进度')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('guidebook-detail')
    expect(router.currentRoute.value.params.guidebookId).toBe('88')
    wrapper.unmount()
  })
})

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import GuidebookView from '@/views/GuidebookView.vue'
import * as guidebookApi from '@/api/guidebook'
import { openGuidebookSocket } from '@/utils/guidebookWs'
import type { GuidebookDetail } from '@/types/api'

vi.mock('@/api/guidebook', () => ({
  getGuidebook: vi.fn(),
  retryGuidebook: vi.fn(),
  deleteGuidebook: vi.fn(),
  exportGuidebook: vi.fn()
}))

vi.mock('@/utils/guidebookWs', () => ({
  openGuidebookSocket: vi.fn(() => ({ close: vi.fn() }))
}))

const mockedGet = vi.mocked(guidebookApi.getGuidebook)
const mockedSocket = vi.mocked(openGuidebookSocket)

function detail(overrides: Partial<GuidebookDetail>): GuidebookDetail {
  return {
    guidebookId: '1',
    status: 'draft',
    version: 1,
    title: null,
    intent: null,
    days: null,
    budgetBreakdown: null,
    packingList: null,
    culturalTips: null,
    pitfallTips: null,
    disclaimer: null,
    degradedMode: false,
    degradationReasons: null,
    currentStep: null,
    progress: 100,
    errorMessage: null,
    ...overrides
  }
}

async function mountAt(id = '1'): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/guidebook/:guidebookId', name: 'guidebook-detail', component: GuidebookView },
      { path: '/guidebook', name: 'guidebook', component: GuidebookView },
      { path: '/chat', name: 'chat', component: { template: '<div />' } }
    ]
  })
  await router.push(`/guidebook/${id}`)
  await router.isReady()
  const wrapper = mount(GuidebookView, {
    global: {
      plugins: [router, ElementPlus],
      stubs: { ElTable: true, ElTableColumn: true }
    }
  })
  await flushPromises()
  return { wrapper, router }
}

describe('GuidebookView', () => {
  beforeEach(() => {
    mockedGet.mockReset()
    mockedSocket.mockClear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('生成中展示进度与当前步骤，并启动轮询', async () => {
    const intervalSpy = vi.spyOn(window, 'setInterval')
    mockedGet.mockResolvedValue(
      detail({ status: 'generating', progress: 55, currentStep: 'itinerary_compose', title: null })
    )

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('生成中')
    expect(wrapper.text()).toContain('AI 生成行程')
    expect(intervalSpy.mock.calls.some((call) => call[1] === 2000)).toBe(true)
    wrapper.unmount()
  })

  it('草稿态展示日程、预算与溯源，且不启动轮询', async () => {
    const intervalSpy = vi.spyOn(window, 'setInterval')
    mockedGet.mockResolvedValue(
      detail({
        title: '成都三日',
        days: [
          {
            day: 1,
            nodes: [
              {
                nodeId: 'd1-1',
                timeSlot: { start: '09:00', end: '11:00' },
                type: 'activity',
                place: { name: '宽窄巷子' },
                source: '[K1] 内置资料',
                dataFreshness: '2026-10-19 10:00',
                note: '早点去人少'
              }
            ]
          }
        ],
        budgetBreakdown: {
          currency: 'CNY',
          items: [{ category: '交通', min: 100, max: 200 }],
          total: { min: 100, max: 200 }
        },
        packingList: ['雨具'],
        degradedMode: true,
        degradationReasons: ['weather_unavailable']
      })
    )

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('成都三日')
    expect(wrapper.text()).toContain('宽窄巷子')
    expect(wrapper.text()).toContain('[K1] 内置资料')
    expect(wrapper.text()).toContain('预算拆解')
    expect(wrapper.text()).toContain('天气数据不可用')
    expect(intervalSpy.mock.calls.some((call) => call[1] === 2000)).toBe(false)
    wrapper.unmount()
  })

  it('失败态展示错误并可重试', async () => {
    mockedGet.mockResolvedValue(
      detail({ status: 'failed', errorMessage: '服务器重启，生成任务已中断' })
    )
    vi.mocked(guidebookApi.retryGuidebook).mockResolvedValue({
      guidebookId: '1',
      status: 'generating',
      estimatedSeconds: 30
    })

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('服务器重启，生成任务已中断')
    const retryButton = wrapper.findAll('button').find((b) => b.text().includes('重新生成'))
    expect(retryButton).toBeTruthy()
    await retryButton!.trigger('click')
    await flushPromises()
    expect(guidebookApi.retryGuidebook).toHaveBeenCalledWith('1')
    wrapper.unmount()
  })
})

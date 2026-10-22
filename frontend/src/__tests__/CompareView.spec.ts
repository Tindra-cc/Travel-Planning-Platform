import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import CompareView from '@/views/CompareView.vue'
import * as compareApi from '@/api/compare'
import * as guidebookApi from '@/api/guidebook'
import type { ComparisonDetail, ComparisonVariant, GuidebookDetail } from '@/types/api'

vi.mock('@/api/compare', () => ({
  startComparison: vi.fn(),
  getComparison: vi.fn(),
  mixComparison: vi.fn()
}))

vi.mock('@/api/guidebook', () => ({
  getGuidebook: vi.fn()
}))

const mockedComparison = vi.mocked(compareApi.getComparison)
const mockedMix = vi.mocked(compareApi.mixComparison)
const mockedGetGuidebook = vi.mocked(guidebookApi.getGuidebook)

function variant(overrides: Partial<ComparisonVariant>): ComparisonVariant {
  return {
    guidebookId: '101',
    variant: 'budget',
    status: 'draft',
    progress: 100,
    title: '省钱成都',
    totalBudget: { min: 1000, max: 1200 },
    dayCount: 1,
    highlights: ['宽窄巷子'],
    ...overrides
  }
}

function comparison(overrides: Partial<ComparisonDetail>): ComparisonDetail {
  return {
    comparisonId: '77',
    baseGuidebookId: '5',
    status: 'completed',
    errorMessage: null,
    variants: [
      variant({ guidebookId: '101', variant: 'budget' }),
      variant({ guidebookId: '102', variant: 'balanced', title: '均衡成都' })
    ],
    ...overrides
  }
}

function guidebookDetail(id: string): GuidebookDetail {
  return {
    guidebookId: id,
    status: 'draft',
    version: 1,
    title: '方案',
    intent: null,
    days: [
      {
        day: 1,
        nodes: [
          {
            nodeId: 'd1-1',
            timeSlot: { start: '09:00', end: '11:00' },
            type: 'activity',
            place: { name: '宽窄巷子' },
            source: null,
            dataFreshness: null,
            note: null
          }
        ]
      }
    ],
    budgetBreakdown: null,
    packingList: null,
    culturalTips: null,
    pitfallTips: null,
    disclaimer: null,
    degradedMode: false,
    degradationReasons: null,
    currentStep: 'completed',
    progress: 100,
    errorMessage: null
  }
}

async function mountCompare() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/guidebook/compare/:comparisonId', name: 'compare', component: CompareView },
      {
        path: '/guidebook/:guidebookId',
        name: 'guidebook-detail',
        component: { template: '<div />' }
      }
    ]
  })
  await router.push('/guidebook/compare/77')
  await router.isReady()
  const wrapper = mount(CompareView, {
    global: { plugins: [router, ElementPlus], stubs: { teleport: true } }
  })
  await flushPromises()
  return { wrapper, router }
}

describe('CompareView', () => {
  beforeEach(() => {
    mockedComparison.mockReset()
    mockedMix.mockReset()
    mockedGetGuidebook.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('展示各方案并加载逐日节点', async () => {
    mockedComparison.mockResolvedValue(comparison({}))
    mockedGetGuidebook.mockImplementation(async (id: string) => guidebookDetail(id))

    const { wrapper } = await mountCompare()

    expect(wrapper.text()).toContain('省钱优先')
    expect(wrapper.text()).toContain('均衡方案')
    expect(wrapper.text()).toContain('¥1000 - ¥1200')
    expect(wrapper.text()).toContain('宽窄巷子')
    expect(mockedGetGuidebook).toHaveBeenCalledWith('101')
    expect(mockedGetGuidebook).toHaveBeenCalledWith('102')
    wrapper.unmount()
  })

  it('勾选节点后混搭并跳转到新攻略', async () => {
    mockedComparison.mockResolvedValue(comparison({}))
    mockedGetGuidebook.mockImplementation(async (id: string) => guidebookDetail(id))
    mockedMix.mockResolvedValue({ guidebookId: '200', status: 'draft', conflicts: [] })

    const { wrapper, router } = await mountCompare()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.findAll('input[type="checkbox"]')[0]!.setValue(true)
    await flushPromises()
    expect(wrapper.text()).toContain('已选 1 个节点')

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('混搭所选'))!
      .trigger('click')
    await flushPromises()

    expect(mockedMix).toHaveBeenCalledWith('77', [{ guidebookId: '101', nodeId: 'd1-1' }])
    expect(pushSpy).toHaveBeenCalledWith('/guidebook/200')
    wrapper.unmount()
  })

  it('冲突时展示冲突面板且保留选择', async () => {
    mockedComparison.mockResolvedValue(comparison({}))
    mockedGetGuidebook.mockImplementation(async (id: string) => guidebookDetail(id))
    mockedMix.mockResolvedValue({
      guidebookId: null,
      status: 'blocked',
      conflicts: [
        { type: 'time_overlap', day: 1, nodeIds: ['a', 'b'], message: '时段重叠' }
      ]
    })

    const { wrapper, router } = await mountCompare()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.findAll('input[type="checkbox"]')[0]!.setValue(true)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('混搭所选'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('存在 1 处冲突')
    expect(wrapper.text()).toContain('时段重叠')
    expect(wrapper.text()).toContain('已选 1 个节点')
    expect(pushSpy).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('生成中轮询详情并在完成后停止', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    mockedComparison
      .mockResolvedValueOnce(comparison({
        status: 'processing',
        variants: [variant({ status: 'generating', progress: 40, title: null })]
      }))
      .mockResolvedValueOnce(comparison({ status: 'completed' }))
    mockedGetGuidebook.mockImplementation(async (id: string) => guidebookDetail(id))

    const { wrapper } = await mountCompare()
    expect(wrapper.text()).toContain('生成中')

    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(mockedComparison).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已完成')
    wrapper.unmount()
  })
})

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import GuidebookView from '@/views/GuidebookView.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import * as compareApi from '@/api/compare'
import * as guidebookApi from '@/api/guidebook'
import * as resourceApi from '@/api/resource'
import * as reviewApi from '@/api/review'
import { openGuidebookSocket } from '@/utils/guidebookWs'
import type { GuidebookDetail } from '@/types/api'

vi.mock('@/api/guidebook', () => ({
  getGuidebook: vi.fn(),
  retryGuidebook: vi.fn(),
  deleteGuidebook: vi.fn(),
  exportGuidebook: vi.fn(),
  validateGuidebook: vi.fn(),
  confirmGuidebook: vi.fn(),
  adjustGuidebook: vi.fn(),
  listGuidebookVersions: vi.fn(),
  restoreGuidebookVersion: vi.fn(),
  updateGuidebookNode: vi.fn(),
  addGuidebookNode: vi.fn(),
  deleteGuidebookNode: vi.fn(),
  moveGuidebookNode: vi.fn()
}))

vi.mock('@/api/resource', () => ({
  searchResources: vi.fn(),
  bindResource: vi.fn(),
  validateLinks: vi.fn()
}))

vi.mock('@/api/compare', () => ({
  startComparison: vi.fn(),
  getComparison: vi.fn(),
  mixComparison: vi.fn()
}))

vi.mock('@/api/review', () => ({
  getReviewSummary: vi.fn(),
  submitReview: vi.fn(),
  decideProfileIteration: vi.fn()
}))

vi.mock('@/api/share', () => ({
  createShare: vi.fn(),
  revokeShare: vi.fn(),
  getSharedGuidebook: vi.fn(),
  regenerateFromShare: vi.fn()
}))

vi.mock('@/utils/guidebookWs', () => ({
  openGuidebookSocket: vi.fn(() => ({ close: vi.fn() }))
}))

const mockedGet = vi.mocked(guidebookApi.getGuidebook)
const mockedSocket = vi.mocked(openGuidebookSocket)
const mockedValidate = vi.mocked(guidebookApi.validateGuidebook)
const mockedAdjust = vi.mocked(guidebookApi.adjustGuidebook)
const mockedVersions = vi.mocked(guidebookApi.listGuidebookVersions)
const mockedRestore = vi.mocked(guidebookApi.restoreGuidebookVersion)
const mockedAddNode = vi.mocked(guidebookApi.addGuidebookNode)
const mockedMoveNode = vi.mocked(guidebookApi.moveGuidebookNode)
const mockedSearchResources = vi.mocked(resourceApi.searchResources)
const mockedBindResource = vi.mocked(resourceApi.bindResource)
const mockedValidateLinks = vi.mocked(resourceApi.validateLinks)
const mockedStartComparison = vi.mocked(compareApi.startComparison)
const mockedReviewSummary = vi.mocked(reviewApi.getReviewSummary)

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
      stubs: {
        ElTable: true,
        ElTableColumn: true,
        ElSelect: true,
        ElOption: true,
        teleport: true
      }
    }
  })
  await flushPromises()
  return { wrapper, router }
}

describe('GuidebookView', () => {
  beforeEach(() => {
    mockedGet.mockReset()
    mockedSocket.mockClear()
    mockedValidate.mockReset()
    mockedValidate.mockResolvedValue({ valid: true, conflicts: [] })
    mockedAdjust.mockReset()
    mockedVersions.mockReset()
    mockedRestore.mockReset()
    mockedAddNode.mockReset()
    mockedMoveNode.mockReset()
    mockedSearchResources.mockReset()
    mockedBindResource.mockReset()
    mockedValidateLinks.mockReset()
    mockedValidateLinks.mockResolvedValue({ results: [] })
    mockedStartComparison.mockReset()
    mockedReviewSummary.mockReset()
    mockedReviewSummary.mockResolvedValue({
      guidebookId: '1',
      status: 'completed',
      totalCount: 0,
      executedCount: 0,
      skippedCount: 0,
      rescheduledCount: 0,
      untouchedCount: 0,
      skipRate: 0,
      aiSummary: '本次行程没有可统计的节点。',
      summarySource: 'template',
      profileSuggestion: null,
      review: null
    })
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
                citations: [
                  {
                    code: 'K1',
                    type: 'encyclopedia',
                    title: '宽窄巷子',
                    sourceName: '内置知识',
                    sourceUrl: 'https://example.com/k1',
                    publishedAt: '2026-10-01',
                    summary: '清代古街'
                  }
                ],
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
    expect(wrapper.text()).toContain('[K1] 宽窄巷子')
    expect(wrapper.find('a.citation').attributes('href')).toBe('https://example.com/k1')
    expect(wrapper.text()).toContain('预算拆解')
    // 数据源缺失已由演示数据兜底，不再向用户展示降级提示
    expect(wrapper.text()).not.toContain('天气数据不可用')
    expect(wrapper.text()).not.toContain('模板生成')
    expect(intervalSpy.mock.calls.some((call) => call[1] === 2000)).toBe(false)
    wrapper.unmount()
  })

  it('已确认状态展示行程且不轮询', async () => {
    const intervalSpy = vi.spyOn(window, 'setInterval')
    mockedGet.mockResolvedValue(
      detail({
        status: 'confirmed',
        title: '成都三日',
        progress: 100,
        days: [{ day: 1, nodes: [] }]
      })
    )

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('已确认')
    expect(intervalSpy.mock.calls.some((call) => call[1] === 2000)).toBe(false)
    wrapper.unmount()
  })

  it('导出按钮调用导出接口并传递格式', async () => {
    mockedGet.mockResolvedValue(
      detail({
        title: '成都三日',
        days: [{ day: 1, nodes: [] }]
      })
    )
    vi.mocked(guidebookApi.exportGuidebook).mockResolvedValue({
      format: 'markdown',
      filename: 'guidebook-1.md',
      content: '# 成都三日'
    })
    if (typeof URL.createObjectURL !== 'function') {
      Object.defineProperty(URL, 'createObjectURL', {
        value: vi.fn(() => 'blob:mock'),
        configurable: true
      })
    }
    if (typeof URL.revokeObjectURL !== 'function') {
      Object.defineProperty(URL, 'revokeObjectURL', {
        value: vi.fn(),
        configurable: true
      })
    }
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const { wrapper } = await mountAt()

    const exportButton = wrapper.findAll('button').find((b) => b.text().includes('导出 Markdown'))
    await exportButton!.trigger('click')
    await flushPromises()

    expect(guidebookApi.exportGuidebook).toHaveBeenCalledWith('1', 'markdown')
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

  it('存在冲突时展示面板并禁用确认', async () => {
    mockedGet.mockResolvedValue(
      detail({ title: '成都三日', days: [{ day: 1, nodes: [] }] })
    )
    mockedValidate.mockResolvedValue({
      valid: false,
      conflicts: [{ type: 'time_overlap', day: 1, nodeIds: ['n1', 'n2'], message: '时段重叠' }]
    })

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('存在 1 处冲突')
    expect(wrapper.text()).toContain('时段重叠')
    const confirmButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('确认攻略'))
    expect(confirmButton!.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('AI 调整调用接口并刷新攻略', async () => {
    mockedGet.mockResolvedValue(detail({ days: [{ day: 1, nodes: [] }] }))
    mockedAdjust.mockResolvedValue({
      guidebookId: '1',
      version: 2,
      changeSummary: '把博物馆挪到 Day2',
      conflicts: [],
      changes: []
    })

    const { wrapper } = await mountAt()

    await wrapper.find('.adjust-input input').setValue('把博物馆挪到第二天')
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('AI 调整'))!
      .trigger('click')
    await flushPromises()

    expect(mockedAdjust).toHaveBeenCalledWith('1', '把博物馆挪到第二天')
    expect(mockedGet.mock.calls.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('版本历史展示版本并可回溯', async () => {
    mockedGet.mockResolvedValue(detail({ days: [{ day: 1, nodes: [] }] }))
    mockedVersions.mockResolvedValue([
      {
        version: 2,
        title: '成都三日',
        changeType: 'adjust',
        changeSummary: 'AI 调整',
        createdAt: '2026-10-20 09:00:00'
      }
    ])
    mockedRestore.mockResolvedValue({
      guidebookId: '1',
      version: 3,
      changeSummary: '回溯到 v2',
      conflicts: []
    })
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)

    const { wrapper } = await mountAt()

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('版本历史'))!
      .trigger('click')
    await flushPromises()

    expect(mockedVersions).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('v2')
    expect(wrapper.text()).toContain('AI 调整')

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('回溯'))!
      .trigger('click')
    await flushPromises()

    expect(mockedRestore).toHaveBeenCalledWith('1', 2)
    wrapper.unmount()
  })

  it('新增节点提交地点、类型与时段', async () => {
    mockedGet.mockResolvedValue(detail({ days: [{ day: 1, nodes: [] }] }))
    mockedAddNode.mockResolvedValue({
      guidebookId: '1',
      version: 2,
      changeSummary: '新增节点',
      conflicts: []
    })

    const { wrapper } = await mountAt()

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('新增节点'))!
      .trigger('click')
    await flushPromises()

    await wrapper.find('input[placeholder="例如：宽窄巷子"]').setValue('新地点')
    await wrapper.find('input[placeholder="09:00"]').setValue('09:00')
    await wrapper.find('input[placeholder="11:00"]').setValue('10:00')
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('保存'))!
      .trigger('click')
    await flushPromises()

    expect(mockedAddNode).toHaveBeenCalledWith(
      '1',
      1,
      expect.objectContaining({
        type: 'activity',
        place: { name: '新地点' },
        timeSlot: { start: '09:00', end: '10:00' }
      })
    )
    wrapper.unmount()
  })

  it('同日拖拽结束后调用移动接口并刷新', async () => {
    mockedGet.mockResolvedValue(
      detail({
        days: [
          {
            day: 1,
            nodes: [
              {
                nodeId: 'n1',
                timeSlot: { start: '09:00', end: '10:00' },
                type: 'activity',
                place: { name: '宽窄巷子' },
                source: null,
                dataFreshness: null,
                note: null
              },
              {
                nodeId: 'n2',
                timeSlot: { start: '11:00', end: '12:00' },
                type: 'meal',
                place: { name: '火锅店' },
                source: null,
                dataFreshness: null,
                note: null
              }
            ]
          }
        ]
      })
    )
    mockedMoveNode.mockResolvedValue({
      guidebookId: '1',
      version: 2,
      changeSummary: '移动节点',
      conflicts: []
    })

    const { wrapper } = await mountAt()

    wrapper.findComponent(VueDraggable).vm.$emit('end', { oldIndex: 0, newIndex: 1 })
    await flushPromises()

    expect(mockedMoveNode).toHaveBeenCalledWith('1', 'n2', {
      targetDay: 1,
      targetIndex: 1
    })
    expect(mockedGet.mock.calls.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('已绑定资源的节点展示供应商、价格与预订入口', async () => {
    mockedGet.mockResolvedValue(
      detail({
        title: '成都三日',
        days: [
          {
            day: 1,
            nodes: [
              {
                nodeId: 'd1-1',
                timeSlot: { start: '09:00', end: '10:00' },
                type: 'activity',
                place: { name: '宽窄巷子' },
                resourceSlotId: 'r70001',
                source: null,
                dataFreshness: null,
                note: null,
                resource: {
                  resourceSlotId: 'r70001',
                  type: 'ticket',
                  supplier: 'Booking 演示',
                  priceRange: { min: 800, max: 1200, currency: 'CNY' },
                  availability: 'available',
                  refundPolicy: '入住前 7 天可取消',
                  bookingLink: '/api/link/click?rid=r70001&sig=abc',
                  freshness: '2026-10-20 09:00:00'
                }
              }
            ]
          }
        ]
      })
    )
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('Booking 演示')
    expect(wrapper.text()).toContain('可预订')
    expect(wrapper.text()).toContain('800')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '预订')!
      .trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(openSpy).toHaveBeenCalledWith(
      '/api/link/click?rid=r70001&sig=abc',
      '_blank',
      'noopener'
    )
    wrapper.unmount()
  })

  it('编辑态可选择并绑定资源到节点', async () => {
    mockedGet.mockResolvedValue(
      detail({
        intent: { destination: '成都' },
        days: [
          {
            day: 1,
            nodes: [
              {
                nodeId: 'd1-1',
                timeSlot: { start: '18:00', end: '19:00' },
                type: 'meal',
                place: { name: '火锅店' },
                resourceSlotId: null,
                source: null,
                dataFreshness: null,
                note: null
              }
            ]
          }
        ]
      })
    )
    mockedSearchResources.mockResolvedValue({
      list: [
        {
          resourceSlotId: 'r70011',
          type: 'dining',
          supplier: '大众点评 演示',
          priceRange: { min: 80, max: 150, currency: 'CNY' },
          availability: 'available',
          refundPolicy: '可退',
          bookingLink: '/api/link/click?rid=r70011&sig=x',
          freshness: null
        }
      ],
      page: 1,
      pageSize: 10,
      total: 1
    })
    mockedBindResource.mockResolvedValue({
      guidebookId: '1',
      version: 2,
      changeSummary: '绑定资源：火锅店',
      conflicts: []
    })

    const { wrapper } = await mountAt()

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('绑定资源'))!
      .trigger('click')
    await flushPromises()

    expect(mockedSearchResources).toHaveBeenCalledWith({
      type: 'dining',
      destination: '成都',
      page: 1,
      pageSize: 10
    })
    expect(wrapper.text()).toContain('大众点评 演示')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '绑定')!
      .trigger('click')
    await flushPromises()

    expect(mockedBindResource).toHaveBeenCalledWith('1', 'd1-1', 'r70011')
    expect(mockedGet.mock.calls.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('已确认攻略不显示编辑与确认入口', async () => {
    mockedGet.mockResolvedValue(
      detail({
        status: 'confirmed',
        title: '成都三日',
        days: [{ day: 1, nodes: [] }]
      })
    )

    const { wrapper } = await mountAt()

    expect(wrapper.text()).not.toContain('AI 调整')
    expect(wrapper.text()).not.toContain('确认攻略')
    expect(mockedValidate).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('生成对比方案：默认三套并跳转对比页', async () => {
    mockedGet.mockResolvedValue(detail({ status: 'draft', days: [{ day: 1, nodes: [] }] }))
    mockedStartComparison.mockResolvedValue({ comparisonId: '77', guidebooks: [] })

    const { wrapper, router } = await mountAt()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('生成对比方案'))!
      .trigger('click')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成')!
      .trigger('click')
    await flushPromises()

    expect(mockedStartComparison).toHaveBeenCalledWith('1', ['budget', 'balanced', 'luxury'])
    expect(pushSpy).toHaveBeenCalledWith('/guidebook/compare/77')
    wrapper.unmount()
  })

  it('只在 completed 状态渲染复盘区并请求摘要', async () => {
    mockedGet.mockResolvedValue(
      detail({ status: 'confirmed', title: '成都三日', days: [{ day: 1, nodes: [] }] })
    )

    const first = await mountAt()

    expect(first.wrapper.find('.review-panel').exists()).toBe(false)
    expect(mockedReviewSummary).not.toHaveBeenCalled()
    first.wrapper.unmount()

    mockedGet.mockResolvedValue(
      detail({ status: 'completed', title: '成都三日', days: [{ day: 1, nodes: [] }] })
    )
    const second = await mountAt()

    expect(second.wrapper.find('.review-panel').exists()).toBe(true)
    expect(mockedReviewSummary).toHaveBeenCalledWith('1')
    second.wrapper.unmount()
  })

  it('已确认攻略仍可生成对比方案', async () => {
    mockedGet.mockResolvedValue(
      detail({ status: 'confirmed', title: '成都三日', days: [{ day: 1, nodes: [] }] })
    )

    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('生成对比方案')
    wrapper.unmount()
  })

  it('已确认、行程中、已完成状态显示分享入口', async () => {
    for (const status of ['confirmed', 'executing', 'completed']) {
      mockedGet.mockResolvedValue(
        detail({ status, title: '成都三日', days: [{ day: 1, nodes: [] }] })
      )

      const { wrapper } = await mountAt()

      expect(
        wrapper.findAll('button').some((b) => b.text() === '分享'),
        `${status} 应显示分享入口`
      ).toBe(true)
      wrapper.unmount()
    }
  })

  it('草稿与生成中不显示分享入口', async () => {
    for (const status of ['draft', 'generating']) {
      mockedGet.mockResolvedValue(detail({ status }))

      const { wrapper } = await mountAt()

      expect(
        wrapper.findAll('button').some((b) => b.text() === '分享'),
        `${status} 不应显示分享入口`
      ).toBe(false)
      wrapper.unmount()
    }
  })

  it('点击分享打开弹窗并传入当前攻略 ID', async () => {
    mockedGet.mockResolvedValue(
      detail({ status: 'confirmed', title: '成都三日', days: [{ day: 1, nodes: [] }] })
    )

    const { wrapper } = await mountAt()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '分享')!
      .trigger('click')
    await flushPromises()

    const dialog = wrapper.findComponent(ShareDialog)
    expect(dialog.exists()).toBe(true)
    expect(dialog.props('guidebookId')).toBe('1')
    wrapper.unmount()
  })
})

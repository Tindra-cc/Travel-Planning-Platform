import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import ElementPlus, { ElMessageBox } from 'element-plus'
import GuidebookView from '@/views/GuidebookView.vue'
import * as guidebookApi from '@/api/guidebook'
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
})

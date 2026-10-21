import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import TripCompanionView from '@/views/TripCompanionView.vue'
import * as companionApi from '@/api/companion'
import { ApiError } from '@/api/request'
import type { TodayNode, TripCompanion, TripSuggestion } from '@/types/api'

vi.mock('@/api/companion', () => ({
  getTripCompanion: vi.fn(),
  refreshSuggestions: vi.fn(),
  acceptSuggestion: vi.fn(),
  rejectSuggestion: vi.fn(),
  markNodeStatus: vi.fn()
}))

const mockedGet = vi.mocked(companionApi.getTripCompanion)
const mockedRefresh = vi.mocked(companionApi.refreshSuggestions)
const mockedAccept = vi.mocked(companionApi.acceptSuggestion)
const mockedReject = vi.mocked(companionApi.rejectSuggestion)
const mockedMark = vi.mocked(companionApi.markNodeStatus)

function todayNode(overrides: Partial<TodayNode> = {}): TodayNode {
  return {
    nodeId: 'n1',
    day: 1,
    timeSlotStart: '09:00',
    timeSlotEnd: '11:00',
    type: 'activity',
    placeName: '宽窄巷子',
    note: '早点去人少',
    executionStatus: null,
    markedAt: null,
    ...overrides
  }
}

function suggestion(overrides: Partial<TripSuggestion> = {}): TripSuggestion {
  return {
    suggestionId: '9',
    type: 'weather_relocate',
    reason: '今天有雨',
    action: '把宽窄巷子移到 Day2',
    operations: [
      { op: 'move_node', nodeId: 'n1', toDay: 2, timeSlot: null, nodeB: null }
    ],
    alternatives: null,
    evidence: null,
    status: 'pending',
    createdAt: '2026-10-21T07:00:00',
    expiresAt: '2026-10-21T23:59:59',
    ...overrides
  }
}

function companion(overrides: Partial<TripCompanion> = {}): TripCompanion {
  return {
    guidebookId: '5',
    status: 'executing',
    destination: '成都',
    startDate: '2026-10-21',
    endDate: '2026-10-22',
    currentDay: 1,
    executedCount: 0,
    skippedCount: 0,
    rescheduledCount: 0,
    totalCount: 2,
    progress: 0,
    todayNodes: [todayNode()],
    suggestions: [suggestion()],
    ...overrides
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/trip/:guidebookId', name: 'trip-companion', component: TripCompanionView },
      { path: '/guidebook/:guidebookId', name: 'guidebook-detail', component: { template: '<div />' } }
    ]
  })
  await router.push('/trip/5')
  await router.isReady()
  const wrapper = mount(TripCompanionView, {
    global: { plugins: [router, ElementPlus], stubs: { teleport: true } }
  })
  await flushPromises()
  return { wrapper, router }
}

function findButton(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((b) => b.text() === text)
  if (!button) throw new Error(`button not found: ${text}`)
  return button
}

describe('TripCompanionView', () => {
  beforeEach(() => {
    mockedGet.mockReset()
    mockedRefresh.mockReset()
    mockedAccept.mockReset()
    mockedReject.mockReset()
    mockedMark.mockReset()
    mockedRefresh.mockResolvedValue({ guidebookId: '5', created: 0 })
    mockedGet.mockResolvedValue(companion())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('进入页面只刷新一次并展示今日节点与建议', async () => {
    const { wrapper } = await mountView()

    expect(mockedRefresh).toHaveBeenCalledTimes(1)
    expect(mockedGet).toHaveBeenCalledWith('5')
    expect(wrapper.text()).toContain('成都')
    expect(wrapper.text()).toContain('第 1 天')
    expect(wrapper.text()).toContain('宽窄巷子')
    expect(wrapper.text()).toContain('今天有雨')
    expect(wrapper.text()).toContain('天气调整')
    wrapper.unmount()
  })

  it('接受带操作的建议后重新拉取行程', async () => {
    mockedAccept.mockResolvedValue({
      suggestionId: '9',
      status: 'accepted',
      applied: true,
      version: 2,
      summary: '把宽窄巷子移到 Day2'
    })
    const { wrapper } = await mountView()

    await findButton(wrapper, '接受').trigger('click')
    await flushPromises()

    expect(mockedAccept).toHaveBeenCalledWith('5', '9')
    expect(mockedGet).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('提示类建议只标记已接受，不刷新行程', async () => {
    mockedAccept.mockResolvedValue({
      suggestionId: '9',
      status: 'accepted',
      applied: false,
      version: 2,
      summary: null
    })
    const { wrapper } = await mountView()

    await findButton(wrapper, '接受').trigger('click')
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('已接受（提示，未改行程）')
    wrapper.unmount()
  })

  it('接受冲突时展示服务端消息并保留建议卡片', async () => {
    mockedAccept.mockRejectedValue(new ApiError(42200, '调整后存在 1 处冲突，请先处理'))
    const { wrapper } = await mountView()

    await findButton(wrapper, '接受').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('调整后存在 1 处冲突，请先处理')
    expect(wrapper.findAll('button').some((b) => b.text() === '接受')).toBe(true)
    expect(mockedGet).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('拒绝建议后标记为已忽略', async () => {
    mockedReject.mockResolvedValue({
      suggestionId: '9',
      status: 'rejected',
      applied: false,
      version: null,
      summary: null
    })
    const { wrapper } = await mountView()

    await findButton(wrapper, '拒绝').trigger('click')
    await flushPromises()

    expect(mockedReject).toHaveBeenCalledWith('5', '9')
    expect(wrapper.text()).toContain('已忽略')
    wrapper.unmount()
  })

  it('标记节点后就地更新状态与进度', async () => {
    mockedMark.mockResolvedValue({
      guidebookId: '5',
      nodeId: 'n1',
      status: 'executed',
      markedAt: '2026-10-21T10:00:00',
      progress: 50
    })
    const { wrapper } = await mountView()

    await findButton(wrapper, '已执行').trigger('click')
    await flushPromises()

    expect(mockedMark).toHaveBeenCalledWith('5', 'n1', 'executed')
    expect(wrapper.text()).toContain('已执行 1')
    expect(wrapper.text()).toContain('更新于 2026-10-21 10:00')
    wrapper.unmount()
  })

  it('页面隐藏时轮询跳过请求', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    const { wrapper } = await mountView()

    await vi.advanceTimersByTimeAsync(30000)
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledTimes(1)
    hidden.mockRestore()
    wrapper.unmount()
  })

  it('卸载后停止轮询', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const { wrapper } = await mountView()
    wrapper.unmount()

    await vi.advanceTimersByTimeAsync(60000)
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledTimes(1)
  })
})

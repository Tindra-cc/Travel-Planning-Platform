import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import ReviewPanel from '@/components/ReviewPanel.vue'
import * as reviewApi from '@/api/review'
import type { ReviewDto, ReviewSummary } from '@/types/api'

vi.mock('@/api/review', () => ({
  getReviewSummary: vi.fn(),
  submitReview: vi.fn(),
  decideProfileIteration: vi.fn()
}))

const mockedSummary = vi.mocked(reviewApi.getReviewSummary)
const mockedSubmit = vi.mocked(reviewApi.submitReview)
const mockedDecide = vi.mocked(reviewApi.decideProfileIteration)

function review(overrides: Partial<ReviewDto> = {}): ReviewDto {
  return {
    reviewId: '9',
    guidebookId: '5',
    overallScore: 5,
    aiAccuracyScore: 4,
    nodeExecutionScore: 5,
    content: '很贴合我的节奏',
    images: [],
    executedCount: 12,
    skippedCount: 2,
    rescheduledCount: 1,
    untouchedCount: 0,
    totalCount: 15,
    iterationStatus: 'pending',
    profileVersionAfter: null,
    decidedAt: null,
    createdAt: '2026-10-21T20:00:00',
    updatedAt: '2026-10-21T20:00:00',
    ...overrides
  }
}

function summary(overrides: Partial<ReviewSummary> = {}): ReviewSummary {
  return {
    guidebookId: '5',
    status: 'completed',
    totalCount: 15,
    executedCount: 12,
    skippedCount: 2,
    rescheduledCount: 1,
    untouchedCount: 0,
    skipRate: 0.1333,
    aiSummary: '本次行程执行顺利，节奏略紧。',
    summarySource: 'ai',
    profileSuggestion: null,
    review: null,
    ...overrides
  }
}

function suggestion() {
  return {
    fields: { pace: 'relaxed' },
    reason: '本次行程跳过率较高且每日节点偏多',
    confidence: 0.65
  }
}

async function mountPanel() {
  const wrapper = mount(ReviewPanel, {
    props: { guidebookId: '5' },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  return wrapper
}

describe('ReviewPanel', () => {
  beforeEach(() => {
    mockedSummary.mockReset()
    mockedSubmit.mockReset()
    mockedDecide.mockReset()
    vi.restoreAllMocks()
  })

  it('展示统计、摘要与来源标签', async () => {
    mockedSummary.mockResolvedValue(summary({ untouchedCount: 1 }))

    const wrapper = await mountPanel()

    expect(wrapper.text()).toContain('本次行程执行顺利')
    expect(wrapper.text()).toContain('AI 生成，仅供参考')
    expect(wrapper.text()).toContain('另有 1 个节点未标记')
    expect(wrapper.text()).toContain('提交评价')
    wrapper.unmount()
  })

  it('模板摘要标注来源且不显示 AI 标签', async () => {
    mockedSummary.mockResolvedValue(
      summary({ summarySource: 'template', aiSummary: '共 15 个节点，已执行 12。' })
    )

    const wrapper = await mountPanel()

    expect(wrapper.text()).toContain('模板摘要')
    expect(wrapper.text()).not.toContain('AI 生成')
    wrapper.unmount()
  })

  it('已有评价时回填表单并显示更新按钮', async () => {
    mockedSummary.mockResolvedValue(summary({ review: review() }))

    const wrapper = await mountPanel()

    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value)
      .toBe('很贴合我的节奏')
    expect(wrapper.text()).toContain('更新评价')
    wrapper.unmount()
  })

  it('提交评价后重新加载摘要', async () => {
    mockedSummary.mockResolvedValue(summary({ review: review() }))
    mockedSubmit.mockResolvedValue(review())
    const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }))

    const wrapper = await mountPanel()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('更新评价'))!
      .trigger('click')
    await flushPromises()

    expect(mockedSubmit).toHaveBeenCalledWith({
      guidebookId: '5',
      overallScore: 5,
      aiAccuracyScore: 4,
      nodeExecutionScore: 5,
      content: '很贴合我的节奏'
    })
    expect(mockedSummary).toHaveBeenCalledTimes(2)
    expect(success).toHaveBeenCalledWith('评价已保存')
    wrapper.unmount()
  })

  it('总体评分为 0 时不发请求', async () => {
    mockedSummary.mockResolvedValue(summary())
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({ close: vi.fn() }))

    const wrapper = await mountPanel()
    const button = wrapper.findAll('button').find((b) => b.text().includes('提交评价'))!
    // 绕过禁用态，验证方法内的兜底校验
    ;(button.element as HTMLButtonElement).disabled = false
    await button.trigger('click')
    await flushPromises()

    expect(mockedSubmit).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('接受建议时调用决策接口并提示版本', async () => {
    mockedSummary.mockResolvedValue(
      summary({ profileSuggestion: suggestion(), review: review() })
    )
    mockedDecide.mockResolvedValue({ profileVersion: 4 })
    const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }))

    const wrapper = await mountPanel()
    expect(wrapper.text()).toContain('画像建议')
    expect(wrapper.text()).toContain('节奏：轻松')

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('接受并更新画像'))!
      .trigger('click')
    await flushPromises()

    expect(mockedDecide).toHaveBeenCalledWith('5', true)
    expect(success).toHaveBeenCalledWith('画像已更新到 v4')
    expect(mockedSummary).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('拒绝建议时画像版本不变', async () => {
    mockedSummary.mockResolvedValue(
      summary({ profileSuggestion: suggestion(), review: review() })
    )
    mockedDecide.mockResolvedValue({ profileVersion: 3 })
    const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }))

    const wrapper = await mountPanel()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('暂不调整'))!
      .trigger('click')
    await flushPromises()

    expect(mockedDecide).toHaveBeenCalledWith('5', false)
    expect(success).toHaveBeenCalledWith('已保留当前画像')
    wrapper.unmount()
  })

  it('已决策的建议以只读提示展示', async () => {
    mockedSummary.mockResolvedValue(
      summary({
        profileSuggestion: suggestion(),
        review: review({
          iterationStatus: 'accepted',
          profileVersionAfter: 4,
          decidedAt: '2026-10-21T21:00:00'
        })
      })
    )

    const wrapper = await mountPanel()

    expect(wrapper.text()).toContain('已接受建议，画像更新到 v4')
    expect(wrapper.text()).not.toContain('接受并更新画像')
    wrapper.unmount()
  })

  it('摘要加载失败可重试', async () => {
    mockedSummary.mockRejectedValueOnce(new Error('网络错误'))
    mockedSummary.mockResolvedValueOnce(summary())

    const wrapper = await mountPanel()
    expect(wrapper.text()).toContain('复盘数据加载失败')

    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('重新加载'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('本次行程执行顺利')
    expect(mockedSummary).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})

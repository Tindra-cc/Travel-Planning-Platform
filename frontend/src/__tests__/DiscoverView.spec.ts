import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import DiscoverView from '@/views/DiscoverView.vue'
import * as guideApi from '@/api/guide'
import type { GuidePostItem } from '@/types/api'

vi.mock('@/api/guide', () => ({
  recommendGuides: vi.fn()
}))

const mockedRecommend = vi.mocked(guideApi.recommendGuides)

function post(overrides: Partial<GuidePostItem> = {}): GuidePostItem {
  return {
    postId: '1',
    title: '成都三日慢游',
    destination: '成都',
    coverUrl: null,
    summary: null,
    authorName: '阿茶',
    tags: null,
    likeCount: 12000,
    viewCount: 100,
    durationDays: 3,
    budgetLevel: null,
    ...overrides
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/discover', name: 'discover', component: DiscoverView },
      { path: '/discover/:postId', name: 'guide-detail', component: { template: '<div />' } },
      { path: '/chat', name: 'chat', component: { template: '<div />' } }
    ]
  })
  await router.push('/discover')
  await router.isReady()
  const wrapper = mount(DiscoverView, { global: { plugins: [router, ElementPlus] } })
  await flushPromises()
  return { wrapper, router }
}

describe('DiscoverView', () => {
  beforeEach(() => {
    mockedRecommend.mockReset()
  })

  it('首屏加载推荐卡片并格式化点赞数', async () => {
    mockedRecommend.mockResolvedValue({
      list: [post(), post({ postId: '2', title: '重庆两日', destination: '重庆' })],
      page: 1,
      pageSize: 12,
      total: 2
    })

    const { wrapper } = await mountView()

    expect(mockedRecommend).toHaveBeenCalledWith(1, 12)
    // 首屏前三条进入热门大卡片，普通网格不再重复展示
    expect(wrapper.findAll('.feature-main')).toHaveLength(1)
    expect(wrapper.findAll('.feature-small')).toHaveLength(1)
    expect(wrapper.findAll('.card')).toHaveLength(0)
    expect(wrapper.text()).toContain('成都三日慢游')
    expect(wrapper.text()).toContain('1.2万')
    expect(wrapper.text()).toContain('没有更多了')
    wrapper.unmount()
  })

  it('加载更多追加下一页数据', async () => {
    mockedRecommend
      .mockResolvedValueOnce({
        list: [post(), post({ postId: '2', title: '重庆两日' })],
        page: 1,
        pageSize: 12,
        total: 3
      })
      .mockResolvedValueOnce({
        list: [post({ postId: '3', title: '西安四日' })],
        page: 2,
        pageSize: 12,
        total: 3
      })
    const { wrapper } = await mountView()

    const more = wrapper.findAll('button').find((b) => b.text().includes('加载更多'))
    await more!.trigger('click')
    await flushPromises()

    expect(mockedRecommend).toHaveBeenLastCalledWith(2, 12)
    expect(wrapper.findAll('.card')).toHaveLength(1)
    expect(wrapper.text()).toContain('西安四日')
    wrapper.unmount()
  })

  it('封面加载失败时切换为占位封面', async () => {
    mockedRecommend.mockResolvedValue({
      list: [post({ coverUrl: 'https://example.com/broken.jpg' })],
      page: 1,
      pageSize: 12,
      total: 1
    })
    const { wrapper } = await mountView()

    expect(wrapper.find('.cover-fallback').exists()).toBe(false)
    await wrapper.find('img').trigger('error')

    expect(wrapper.find('.cover-fallback').exists()).toBe(true)
    expect(wrapper.find('.cover-fallback').text()).toBe('成')
    wrapper.unmount()
  })

  it('无数据时展示空态与去对话入口', async () => {
    mockedRecommend.mockResolvedValue({ list: [], page: 1, pageSize: 12, total: 0 })

    const { wrapper, router } = await mountView()

    expect(wrapper.text()).toContain('暂时没有可推荐的攻略')
    const goChat = wrapper.findAll('button').find((b) => b.text().includes('去对话聊聊'))
    await goChat!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/chat')
    wrapper.unmount()
  })
})

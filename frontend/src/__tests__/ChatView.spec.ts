import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import ChatView from '@/views/ChatView.vue'
import * as chatApi from '@/api/chat'
import * as guidebookApi from '@/api/guidebook'
import type { GuidebookDetail, SessionDetail, SessionListItem } from '@/types/api'

vi.mock('@/api/chat', () => ({
  listSessions: vi.fn(),
  createSession: vi.fn(),
  getSession: vi.fn(),
  sendMessage: vi.fn(),
  confirmSession: vi.fn(),
  deleteSession: vi.fn()
}))

vi.mock('@/api/guidebook', () => ({
  getGuidebook: vi.fn(),
  adjustGuidebook: vi.fn()
}))

vi.mock('@/utils/ws', () => ({
  openChatSocket: vi.fn(() => ({ close: vi.fn(), send: vi.fn(), readyState: 3 }))
}))

const mockedList = vi.mocked(chatApi.listSessions)
const mockedCreate = vi.mocked(chatApi.createSession)
const mockedGet = vi.mocked(chatApi.getSession)
const mockedConfirm = vi.mocked(chatApi.confirmSession)
const mockedGetGuidebook = vi.mocked(guidebookApi.getGuidebook)
const mockedAdjust = vi.mocked(guidebookApi.adjustGuidebook)

const session: SessionListItem = {
  sessionId: '11',
  title: '成都两日',
  status: 'ready_to_generate',
  updatedAt: '2026-10-19T10:00:00'
}

function detail(overrides: Partial<SessionDetail> = {}): SessionDetail {
  return {
    sessionId: '11',
    status: 'ready_to_generate',
    guidebookId: null,
    intent: null,
    pendingQuestions: [],
    messages: [],
    ...overrides
  }
}

async function mountView(path = '/chat') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/chat', name: 'chat', component: ChatView },
      {
        path: '/guidebook/:guidebookId',
        name: 'guidebook-detail',
        component: { template: '<div>guidebook</div>' }
      }
    ]
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ChatView, { global: { plugins: [router, ElementPlus] } })
  await flushPromises()
  return { wrapper, router }
}

describe('ChatView', () => {
  beforeEach(() => {
    mockedList.mockReset()
    mockedCreate.mockReset()
    mockedGet.mockReset()
    mockedConfirm.mockReset()
    mockedGetGuidebook.mockReset()
    mockedAdjust.mockReset()
    mockedList.mockResolvedValue({ list: [], page: 1, pageSize: 50, total: 0 })
  })

  it('加载会话列表并展示状态标签', async () => {
    mockedList.mockResolvedValue({ list: [session], page: 1, pageSize: 50, total: 1 })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('成都两日')
    expect(wrapper.text()).toContain('待生成')
    wrapper.unmount()
  })

  it('选中会话后渲染消息、待澄清问题与确认按钮', async () => {
    mockedList.mockResolvedValue({ list: [session], page: 1, pageSize: 50, total: 1 })
    mockedGet.mockResolvedValue(
      detail({
        messages: [
          { messageId: 'm1', role: 'user', content: '我想去成都', createdAt: '2026-10-19' },
          { messageId: 'm2', role: 'assistant', content: '好的，几天？', createdAt: '2026-10-19' }
        ],
        pendingQuestions: [{ questionId: 'q1', text: '什么时候出发？' }]
      })
    )
    const { wrapper } = await mountView()

    await wrapper.find('.session-list li').trigger('click')
    await flushPromises()

    expect(mockedGet).toHaveBeenCalledWith('11')
    expect(wrapper.text()).toContain('我想去成都')
    expect(wrapper.text()).toContain('好的，几天？')
    expect(wrapper.text()).toContain('什么时候出发？')
    expect(wrapper.text()).toContain('确认生成攻略')
    wrapper.unmount()
  })

  it('确认生成后跳转到攻略详情页', async () => {
    mockedList.mockResolvedValue({ list: [session], page: 1, pageSize: 50, total: 1 })
    mockedGet.mockResolvedValue(detail())
    mockedConfirm.mockResolvedValue({
      sessionId: '11',
      status: 'generating',
      guidebookId: '5'
    })
    const { wrapper, router } = await mountView()

    await wrapper.find('.session-list li').trigger('click')
    await flushPromises()
    const confirm = wrapper.findAll('button').find((b) => b.text().includes('确认生成攻略'))
    await confirm!.trigger('click')
    await flushPromises()

    expect(mockedConfirm).toHaveBeenCalledWith('11')
    expect(router.currentRoute.value.path).toBe('/guidebook/5')
    expect(router.currentRoute.value.query.sessionId).toBe('11')
    wrapper.unmount()
  })

  it('生成中禁用输入与发送', async () => {
    mockedList.mockResolvedValue({ list: [session], page: 1, pageSize: 50, total: 1 })
    mockedGet.mockResolvedValue(detail({ status: 'generating' }))
    const { wrapper } = await mountView()

    await wrapper.find('.session-list li').trigger('click')
    await flushPromises()

    expect(wrapper.find('textarea').attributes('disabled')).toBeDefined()
    const send = wrapper.findAll('button').find((b) => b.text() === '发送')
    expect(send!.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('无会话时发送会创建新会话', async () => {
    mockedCreate.mockResolvedValue({
      sessionId: '12',
      status: 'collecting',
      messages: [
        { messageId: 'm1', role: 'user', content: '去成都', createdAt: '2026-10-19' },
        { messageId: 'm2', role: 'assistant', content: '想去哪个目的地？', createdAt: '2026-10-19' }
      ],
      pendingQuestions: [{ questionId: 'q1', text: '想去哪个目的地？' }]
    })
    const { wrapper } = await mountView()

    await wrapper.find('textarea').setValue('去成都')
    const send = wrapper.findAll('button').find((b) => b.text() === '发送')
    await send!.trigger('click')
    await flushPromises()

    expect(mockedCreate).toHaveBeenCalledWith('去成都', undefined)
    expect(wrapper.text()).toContain('想去哪个目的地？')
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')
    wrapper.unmount()
  })

  it('从推荐进入时把 postId 作为参考传给创建接口', async () => {
    mockedCreate.mockResolvedValue({
      sessionId: '12',
      status: 'collecting',
      messages: [],
      pendingQuestions: []
    })
    const { wrapper } = await mountView(
      '/chat?postId=42&title=成都四日&destination=成都&durationDays=4'
    )

    expect(wrapper.text()).toContain('参考推荐《成都四日》')
    await wrapper.find('textarea').setValue('想去成都')
    const send = wrapper.findAll('button').find((b) => b.text() === '发送')
    await send!.trigger('click')
    await flushPromises()

    expect(mockedCreate).toHaveBeenCalledWith('想去成都', '42')
    wrapper.unmount()
  })

  it('绑定可编辑攻略时展示调整入口，调整结果只做本地展示', async () => {
    mockedList.mockResolvedValue({ list: [session], page: 1, pageSize: 50, total: 1 })
    mockedGet.mockResolvedValue(
      detail({ status: 'generated', guidebookId: '5' })
    )
    mockedGetGuidebook.mockResolvedValue({ guidebookId: '5', status: 'draft' } as GuidebookDetail)
    mockedAdjust.mockResolvedValue({
      guidebookId: '5',
      version: 2,
      changeSummary: '把博物馆挪到 Day3',
      conflicts: [],
      changes: []
    })
    const { wrapper } = await mountView()

    await wrapper.find('.session-list li').trigger('click')
    await flushPromises()

    expect(mockedGetGuidebook).toHaveBeenCalledWith('5')
    const adjustEntry = wrapper
      .findAll('button')
      .find((b) => b.text().includes('调整当前攻略'))
    expect(adjustEntry).toBeTruthy()

    await adjustEntry!.trigger('click')
    await wrapper.find('textarea').setValue('博物馆挪到第三天')
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('执行调整'))!
      .trigger('click')
    await flushPromises()

    expect(mockedAdjust).toHaveBeenCalledWith('5', '博物馆挪到第三天')
    expect(wrapper.text()).toContain('已按你的要求调整攻略（v2）：把博物馆挪到 Day3')
    expect(mockedCreate).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已确认攻略不展示调整入口但可跳转查看', async () => {
    mockedList.mockResolvedValue({ list: [session], page: 1, pageSize: 50, total: 1 })
    mockedGet.mockResolvedValue(
      detail({ status: 'generated', guidebookId: '5' })
    )
    mockedGetGuidebook.mockResolvedValue({
      guidebookId: '5',
      status: 'confirmed'
    } as GuidebookDetail)
    const { wrapper, router } = await mountView()

    await wrapper.find('.session-list li').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('调整当前攻略')
    const viewButton = wrapper.findAll('button').find((b) => b.text().includes('查看攻略'))
    expect(viewButton).toBeTruthy()

    await viewButton!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/guidebook/5')
    wrapper.unmount()
  })
})

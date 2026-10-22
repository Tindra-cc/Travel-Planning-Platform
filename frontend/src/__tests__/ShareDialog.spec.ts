import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import ShareDialog from '@/components/ShareDialog.vue'
import * as shareApi from '@/api/share'

vi.mock('@/api/share', () => ({
  createShare: vi.fn(),
  revokeShare: vi.fn()
}))

const mockedCreate = vi.mocked(shareApi.createShare)
const mockedRevoke = vi.mocked(shareApi.revokeShare)

function mountDialog() {
  return mount(ShareDialog, {
    props: { guidebookId: '1', modelValue: true },
    global: {
      plugins: [ElementPlus],
      stubs: { teleport: true }
    }
  })
}

function buttonTexts(wrapper: ReturnType<typeof mountDialog>) {
  return wrapper.findAll('button').map((b) => b.text())
}

describe('ShareDialog', () => {
  beforeEach(() => {
    mockedCreate.mockReset()
    mockedRevoke.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('默认链接渠道，未生成时只显示生成按钮', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(buttonTexts(wrapper)).toContain('生成分享')
    expect(buttonTexts(wrapper)).not.toContain('撤回分享')
    expect(wrapper.find('input[readonly]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('创建成功后展示链接，二维码为空时给出提示', async () => {
    mockedCreate.mockResolvedValue({
      shareId: 'abc123',
      shareUrl: 'http://localhost:5173/share/abc123',
      qrcodeUrl: null
    })

    const wrapper = mountDialog()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成分享')!
      .trigger('click')
    await flushPromises()

    expect(mockedCreate).toHaveBeenCalledWith('1', 'link')
    const linkInput = wrapper.find('input[readonly]').element as HTMLInputElement
    expect(linkInput.value).toBe('http://localhost:5173/share/abc123')
    // 未配置二维码服务时前端本地生成 SVG
    expect(wrapper.find('.qrcode svg').exists()).toBe(true)
    expect(wrapper.find('.qrcode img').exists()).toBe(false)
    wrapper.unmount()
  })

  it('选择二维码渠道后按二维码创建分享', async () => {
    mockedCreate.mockResolvedValue({
      shareId: 'abc123',
      shareUrl: 'http://localhost:5173/share/abc123',
      qrcodeUrl: 'https://qr.example.com/abc123.png'
    })

    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.find('input[value="qrcode"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成分享')!
      .trigger('click')
    await flushPromises()

    expect(mockedCreate).toHaveBeenCalledWith('1', 'qrcode')
    expect(wrapper.find('.qrcode img').attributes('src')).toBe(
      'https://qr.example.com/abc123.png'
    )
    wrapper.unmount()
  })

  it('复制成功提示并写入剪贴板', async () => {
    mockedCreate.mockResolvedValue({
      shareId: 'abc123',
      shareUrl: 'http://localhost:5173/share/abc123',
      qrcodeUrl: null
    })
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    })
    const successSpy = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)

    const wrapper = mountDialog()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成分享')!
      .trigger('click')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '复制')!
      .trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('http://localhost:5173/share/abc123')
    expect(successSpy).toHaveBeenCalledWith('链接已复制')
    wrapper.unmount()
  })

  it('复制失败时给出警告且不抛异常', async () => {
    mockedCreate.mockResolvedValue({
      shareId: 'abc123',
      shareUrl: 'http://localhost:5173/share/abc123',
      qrcodeUrl: null
    })
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
      configurable: true
    })
    const warningSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as never)

    const wrapper = mountDialog()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成分享')!
      .trigger('click')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '复制')!
      .trigger('click')
    await flushPromises()

    expect(warningSpy).toHaveBeenCalledWith('复制失败，请手动选择链接复制')
    wrapper.unmount()
  })

  it('撤回后关闭弹窗并清空结果', async () => {
    mockedCreate.mockResolvedValue({
      shareId: 'abc123',
      shareUrl: 'http://localhost:5173/share/abc123',
      qrcodeUrl: null
    })
    mockedRevoke.mockResolvedValue(undefined)

    const wrapper = mountDialog()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成分享')!
      .trigger('click')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '撤回分享')!
      .trigger('click')
    await flushPromises()

    expect(mockedRevoke).toHaveBeenCalledWith('1', 'abc123')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted?.[emitted.length - 1]).toEqual([false])
    wrapper.unmount()
  })

  it('关闭后重新打开会重置渠道与结果', async () => {
    mockedCreate.mockResolvedValue({
      shareId: 'abc123',
      shareUrl: 'http://localhost:5173/share/abc123',
      qrcodeUrl: null
    })

    const wrapper = mountDialog()
    await flushPromises()
    await wrapper.find('input[value="qrcode"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成分享')!
      .trigger('click')
    await flushPromises()

    wrapper.findComponent({ name: 'ElDialog' }).vm.$emit('closed')
    await flushPromises()

    expect(wrapper.find('input[readonly]').exists()).toBe(false)
    expect(buttonTexts(wrapper)).toContain('生成分享')
    const linkRadio = wrapper.find('input[value="link"]')
    expect((linkRadio.element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
  })
})

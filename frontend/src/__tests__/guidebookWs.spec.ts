import { describe, it, expect, vi, beforeEach } from 'vitest'
import { openGuidebookSocket } from '@/utils/guidebookWs'
import { TOKEN_KEY } from '@/api/request'

class MockWebSocket {
  static instances: MockWebSocket[] = []
  url: string
  onmessage: ((ev: MessageEvent) => void) | null = null
  onclose: (() => void) | null = null

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }
}

function lastSocket(): MockWebSocket {
  const socket = MockWebSocket.instances[0]
  if (!socket) throw new Error('WebSocket not constructed')
  return socket
}

function emit(socket: MockWebSocket, payload: unknown) {
  socket.onmessage!({ data: JSON.stringify(payload) } as MessageEvent)
}

describe('openGuidebookSocket', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    localStorage.clear()
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  it('拼装 ws URL 并对 token 做编码', () => {
    localStorage.setItem(TOKEN_KEY, 'a b&c')

    openGuidebookSocket('5', {})

    const url = lastSocket().url
    expect(url).toBe(
      `ws://${location.host}/api/ws/guidebook/5?token=${encodeURIComponent('a b&c')}`
    )
  })

  it('分发 progress / done / error 事件', () => {
    const onProgress = vi.fn()
    const onDone = vi.fn()
    const onError = vi.fn()
    openGuidebookSocket('5', { onProgress, onDone, onError })
    const socket = lastSocket()

    emit(socket, {
      type: 'guidebook_progress',
      guidebookId: '5',
      status: 'generating',
      currentStep: 'itinerary',
      progress: 40
    })
    emit(socket, {
      type: 'guidebook_done',
      guidebookId: '5',
      status: 'draft',
      progress: 100
    })
    emit(socket, { type: 'error', guidebookId: '5', message: '模型超时' })

    expect(onProgress).toHaveBeenCalledWith(
      expect.objectContaining({ currentStep: 'itinerary', progress: 40 })
    )
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ status: 'draft' }))
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: '模型超时' }))
  })

  it('非法 JSON 与未知事件被忽略', () => {
    const onProgress = vi.fn()
    openGuidebookSocket('5', { onProgress })
    const socket = lastSocket()

    socket.onmessage!({ data: 'not-json' } as MessageEvent)
    emit(socket, { type: 'unknown' })

    expect(onProgress).not.toHaveBeenCalled()
  })

  it('关闭时触发 onClose', () => {
    const onClose = vi.fn()
    openGuidebookSocket('5', { onClose })

    lastSocket().onclose!()

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

import { TOKEN_KEY } from '@/api/request'

export interface GuidebookProgressEvent {
  type: 'guidebook_progress'
  guidebookId: string
  status: string
  currentStep?: string | null
  progress?: number | null
}

export interface GuidebookDoneEvent {
  type: 'guidebook_done'
  guidebookId: string
  status: string
  progress?: number
}

export interface GuidebookErrorEvent {
  type: 'error'
  guidebookId?: string
  status?: string
  message: string
}

export interface GuidebookSocketHandlers {
  onProgress?: (event: GuidebookProgressEvent) => void
  onDone?: (event: GuidebookDoneEvent) => void
  onError?: (event: GuidebookErrorEvent) => void
  onClose?: () => void
}

/** 连接 /api/ws/guidebook/{guidebookId}；断开后由页面回退到轮询。 */
export function openGuidebookSocket(
  guidebookId: string,
  handlers: GuidebookSocketHandlers
): WebSocket {
  const token = localStorage.getItem(TOKEN_KEY) ?? ''
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${location.host}/api/ws/guidebook/${guidebookId}?token=${encodeURIComponent(token)}`
  const socket = new WebSocket(url)

  socket.onmessage = (ev) => {
    let payload: GuidebookProgressEvent | GuidebookDoneEvent | GuidebookErrorEvent
    try {
      payload = JSON.parse(ev.data)
    } catch {
      return
    }
    if (payload.type === 'guidebook_progress') {
      handlers.onProgress?.(payload)
    } else if (payload.type === 'guidebook_done') {
      handlers.onDone?.(payload)
    } else if (payload.type === 'error') {
      handlers.onError?.(payload)
    }
  }

  socket.onclose = () => handlers.onClose?.()
  return socket
}

import { TOKEN_KEY } from '@/api/request'

export interface MessageDelta {
  event: 'message_delta'
  messageId: string
  delta: string
  done: boolean
}

export interface MessageDone {
  event: 'message_done'
  messageId: string
  sessionStatus: string
  pendingQuestions: { questionId: string; text: string }[]
}

export interface WsError {
  event: 'error'
  message: string
}

export interface ChatSocketHandlers {
  onDelta?: (event: MessageDelta) => void
  onDone?: (event: MessageDone) => void
  onError?: (event: WsError) => void
  onClose?: () => void
}

/** 连接 /api/ws/chat/{sessionId}，浏览器无法自定义头，故 token 走查询参数。 */
export function openChatSocket(sessionId: string, handlers: ChatSocketHandlers): WebSocket {
  const token = localStorage.getItem(TOKEN_KEY) ?? ''
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${location.host}/api/ws/chat/${sessionId}?token=${encodeURIComponent(token)}`
  const socket = new WebSocket(url)

  socket.onmessage = (ev) => {
    let payload: MessageDelta | MessageDone | WsError
    try {
      payload = JSON.parse(ev.data)
    } catch {
      return
    }
    if (payload.event === 'message_delta') {
      handlers.onDelta?.(payload)
    } else if (payload.event === 'message_done') {
      handlers.onDone?.(payload)
    } else if (payload.event === 'error') {
      handlers.onError?.(payload)
    }
  }

  socket.onclose = () => handlers.onClose?.()
  return socket
}

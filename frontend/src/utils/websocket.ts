import SockJS from 'sockjs-client/dist/sockjs'
import { Client, type IMessage } from '@stomp/stompjs'

export interface DetectionItem {
  type: 'disease' | 'pest'
  classId: number
  className: string
  nameCn: string
  confidence: number
  bbox: { x: number; y: number; width: number; height: number }
}

export interface InferenceResultMessage {
  type: string
  data: {
    inferenceId: string
    cameraId: string
    cameraName: string
    captureTime: string
    frameWidth: number
    frameHeight: number
    detections: DetectionItem[]
    diseaseCount: number
    pestCount: number
  }
}

let client: Client | null = null
let clientToken = '' // 创建当前 client 时使用的 token
let connecting = false

/** 检查 JWT 是否已过期（解码 payload 的 exp 字段） */
function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}
const pendingResolves: Array<() => void> = []
const pendingRejects: Array<(e: Error) => void> = []
const callerIds = new Set<string>()

let nextCallerId = 0
function newCallerId(): string {
  return String(++nextCallerId)
}

export function getWsClient(): Client {
  const token = localStorage.getItem('treeforge_token') || ''
  // token 变化（如刷新后）→ 销毁旧 client，用新 token 重建
  if (client && clientToken !== token) {
    console.log('[WS] Token changed, recreating client')
    try { client.deactivate() } catch { /* ignore */ }
    client = null
    connecting = false
    const err = new Error('Token refreshed, please reconnect')
    pendingRejects.splice(0).forEach(r => r(err))
    pendingResolves.splice(0)
  }
  if (!client) {
    // token 已过期 → 不创建无效连接，直接抛错
    if (!token || isTokenExpired(token)) {
      throw new Error('Token expired, please login again')
    }
    clientToken = token
    console.log('[WS] Creating client, token length:', token.length)
    client = new Client({
      webSocketFactory: () => {
        console.log('[WS] Creating SockJS connection to /ws')
        return new SockJS(`/ws?token=${token}`)
      },
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg) => console.log('[WS-DEBUG]', msg),
    })
  }
  return client
}

export function connectWs(): Promise<string> {
  return new Promise((resolve, reject) => {
    const c = getWsClient()
    const id = newCallerId()
    callerIds.add(id)

    if (c.connected) {
      resolve(id)
      return
    }

    pendingResolves.push(() => resolve(id))
    pendingRejects.push(reject)

    if (!connecting) {
      connecting = true
      c.onConnect = (frame) => {
        connecting = false
        console.log('[WS] Connected ✓')
        const resolvers = [...pendingResolves]
        pendingResolves.length = 0
        pendingRejects.length = 0
        resolvers.forEach(r => r())
      }
      c.onDisconnect = () => {
        console.log('[WS] Disconnected')
      }
      c.onWebSocketClose = (evt) => {
        console.warn('[WS] WebSocket closed:', evt.code, evt.reason)
      }
      c.onWebSocketError = (evt) => {
        console.error('[WS] WebSocket error:', evt)
      }
      c.onStompError = (frame) => {
        connecting = false
        console.error('[WS] STOMP error:', frame.headers['message'], frame.body)
        const err = new Error(frame.headers['message'] || 'WebSocket connection failed')
        const rejecters = [...pendingRejects]
        pendingResolves.length = 0
        pendingRejects.length = 0
        rejecters.forEach(r => r(err))
      }
      c.activate()
    }
  })
}

export function disconnectWs(callerId?: string) {
  if (callerId) callerIds.delete(callerId)
  if (callerIds.size === 0 && client) {
    client.deactivate()
    client = null
    connecting = false
    pendingResolves.length = 0
    pendingRejects.length = 0
  }
}

export function subscribeTopic(
  topic: string,
  callback: (data: InferenceResultMessage) => void
) {
  const c = getWsClient()
  if (!c.connected) {
    console.warn('[WS] subscribeTopic called but not connected:', topic)
    return null
  }
  console.log('[WS] Subscribing to:', topic)
  return c.subscribe(topic, (message: IMessage) => {
    try {
      const parsed = JSON.parse(message.body) as InferenceResultMessage
      console.log('[WS] Received on', topic, ':', parsed.data?.detections?.length, 'detections')
      callback(parsed)
    } catch (e) {
      console.warn('[WS] Failed to parse message:', e, message.body?.substring(0, 200))
    }
  })
}

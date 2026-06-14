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
let connecting = false
const pendingResolves: Array<() => void> = []
const pendingRejects: Array<(e: Error) => void> = []
const callerIds = new Set<string>()

let nextCallerId = 0
function newCallerId(): string {
  return String(++nextCallerId)
}

export function getWsClient(): Client {
  if (!client) {
    const token = localStorage.getItem('treeforge_token') || ''
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

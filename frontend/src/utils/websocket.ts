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
let refCount = 0

export function getWsClient(): Client {
  if (!client) {
    const token = localStorage.getItem('treeforge_token') || ''
    client = new Client({
      webSocketFactory: () => new SockJS(`/ws?token=${token}`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    })
  }
  return client
}

export function connectWs(): Promise<void> {
  return new Promise((resolve, reject) => {
    const c = getWsClient()
    refCount++
    if (c.connected) {
      resolve()
      return
    }
    const origOnConnect = c.onConnect
    c.onConnect = (frame) => {
      origOnConnect?.(frame)
      resolve()
    }
    c.onStompError = (frame) => {
      reject(new Error(frame.headers['message'] || 'WebSocket connection failed'))
    }
    c.activate()
  })
}

export function disconnectWs() {
  refCount--
  if (refCount <= 0 && client) {
    client.deactivate()
    client = null
    refCount = 0
  }
}

export function subscribeTopic(
  topic: string,
  callback: (data: InferenceResultMessage) => void
) {
  const c = getWsClient()
  if (!c.connected) return null
  return c.subscribe(topic, (message: IMessage) => {
    try {
      const parsed = JSON.parse(message.body) as InferenceResultMessage
      callback(parsed)
    } catch (e) {
      console.warn('Failed to parse WebSocket message:', e)
    }
  })
}

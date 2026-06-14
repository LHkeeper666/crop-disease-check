<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import type { Subscription } from '@stomp/stompjs'
import { connectWs, disconnectWs, subscribeTopic, type DetectionItem, type InferenceResultMessage } from '../utils/websocket'

const props = withDefaults(defineProps<{
  cameraId: string
  cameraName: string
  streamUrl?: string   // IP Webcam HTTP 直连地址 (MJPEG)
  active?: boolean
}>(), {
  active: true,
})

const emit = defineEmits<{
  (e: 'status-change', status: string): void
}>()

const imgRef = ref<HTMLImageElement>()
const canvasRef = ref<HTMLCanvasElement>()
const status = ref<'ONLINE' | 'OFFLINE' | 'FAULT'>('OFFLINE')
const diseaseCount = ref(0)
const pestCount = ref(0)
const lastDetectTime = ref('')
const videoAspect = ref(16 / 9) // 默认16:9，实际由检测帧决定

let subscription: Subscription | null = null
let wsCallerId: string | null = null
let aspectFromDetection = false

function onStreamLoad() {
  status.value = 'ONLINE'
  emit('status-change', 'ONLINE')
  // 尝试从 img 元素获取实际宽高比
  if (imgRef.value && imgRef.value.naturalWidth > 0 && !aspectFromDetection) {
    videoAspect.value = imgRef.value.naturalWidth / imgRef.value.naturalHeight
  }
}

function onStreamError() {
  status.value = 'FAULT'
  emit('status-change', 'FAULT')
}

function syncCanvasSize() {
  if (!canvasRef.value) return
  // 优先用 img 元素尺寸，无视频流时回退到 canvas 父容器
  const source = (imgRef.value && imgRef.value.offsetWidth > 0)
    ? imgRef.value
    : canvasRef.value.parentElement
  if (!source) return
  const rect = source.getBoundingClientRect()
  canvasRef.value.width = rect.width
  canvasRef.value.height = rect.height
}

function drawDetections(
  canvas: HTMLCanvasElement,
  detections: DetectionItem[],
  frameWidth: number,
  frameHeight: number
) {
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  // 计算 object-contain 模式下视频实际显示区域（排除黑边）
  const canvasW = canvas.width
  const canvasH = canvas.height
  const frameAspect = frameWidth / frameHeight
  const canvasAspect = canvasW / canvasH

  let drawW: number, drawH: number, offsetX: number, offsetY: number
  if (frameAspect > canvasAspect) {
    drawW = canvasW
    drawH = canvasW / frameAspect
    offsetX = 0
    offsetY = (canvasH - drawH) / 2
  } else {
    drawH = canvasH
    drawW = canvasH * frameAspect
    offsetX = (canvasW - drawW) / 2
    offsetY = 0
  }

  const scaleX = drawW / frameWidth
  const scaleY = drawH / frameHeight

  detections.forEach(det => {
    const x = offsetX + det.bbox.x * scaleX
    const y = offsetY + det.bbox.y * scaleY
    const w = det.bbox.width * scaleX
    const h = det.bbox.height * scaleY

    const color = det.type === 'disease' ? '#ff4444' : '#4488ff'

    ctx.strokeStyle = color
    ctx.lineWidth = 2
    ctx.strokeRect(x, y, w, h)

    const label = `${det.nameCn} ${(det.confidence * 100).toFixed(0)}%`
    ctx.font = '12px sans-serif'
    const textWidth = ctx.measureText(label).width

    ctx.fillStyle = color
    ctx.globalAlpha = 0.7
    ctx.fillRect(x, y - 20, textWidth + 10, 20)
    ctx.globalAlpha = 1

    ctx.fillStyle = '#ffffff'
    ctx.fillText(label, x + 5, y - 5)
  })
}

function handleDetection(msg: InferenceResultMessage) {
  diseaseCount.value = msg.data.diseaseCount || 0
  pestCount.value = msg.data.pestCount || 0
  lastDetectTime.value = msg.data.captureTime || ''

  // 用检测帧的实际宽高更新容器宽高比
  if (msg.data.frameWidth > 0 && msg.data.frameHeight > 0) {
    videoAspect.value = msg.data.frameWidth / msg.data.frameHeight
    aspectFromDetection = true
  }

  if (canvasRef.value) {
    syncCanvasSize()
    drawDetections(
      canvasRef.value,
      msg.data.detections,
      msg.data.frameWidth,
      msg.data.frameHeight
    )
  }
}

function clearCanvas() {
  if (!canvasRef.value) return
  const ctx = canvasRef.value.getContext('2d')
  if (ctx) ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  diseaseCount.value = 0
  pestCount.value = 0
}

function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token')
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

async function startBackendMonitor() {
  try {
    await fetch(`/api/camera/${props.cameraId}/monitor`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ enabled: true, intervalSeconds: 5, confidence: 0.5 }),
    })
  } catch (e) {
    console.warn('启动后端监测失败:', e)
  }
}

async function stopBackendMonitor() {
  try {
    await fetch(`/api/camera/${props.cameraId}/monitor`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ enabled: false }),
    })
  } catch (e) {
    console.warn('停止后端监测失败:', e)
  }
}

async function startMonitoring() {
  if (!props.active) return
  startBackendMonitor()
  try {
    wsCallerId = await connectWs()
    subscription = subscribeTopic(`/topic/camera/${props.cameraId}/detect`, (msg) => {
      handleDetection(msg)
    })
  } catch (e) {
    console.warn('WebSocket connect failed:', e)
  }
}

function stopMonitoring() {
  subscription?.unsubscribe()
  subscription = null
  disconnectWs(wsCallerId ?? undefined)
  wsCallerId = null
  clearCanvas()
  status.value = 'OFFLINE'
  stopBackendMonitor()
}

watch(() => props.active, (val) => {
  if (val) startMonitoring()
  else stopMonitoring()
})

watch(() => props.streamUrl, () => {
  // streamUrl 变化时 img 会自动重新加载
  aspectFromDetection = false
})

onMounted(() => {
  if (props.active) startMonitoring()
})

onUnmounted(() => {
  stopMonitoring()
})
</script>

<template>
  <div class="camera-window group" :style="{ aspectRatio: videoAspect }">
    <!-- MJPEG 直连 IP Webcam，无需任何播放器 -->
    <img
      v-show="streamUrl"
      ref="imgRef"
      :src="streamUrl"
      class="absolute inset-0 w-full h-full object-contain bg-black"
      @load="onStreamLoad"
      @error="onStreamError"
    />
    <canvas ref="canvasRef" class="absolute inset-0 w-full h-full pointer-events-none"></canvas>

    <!-- Top status indicator -->
    <div class="absolute top-2 left-2 flex items-center gap-2 px-2 py-1 rounded-lg bg-black/60 backdrop-blur-sm text-[10px] font-mono">
      <span class="w-2 h-2 rounded-full" :class="{
        'bg-[#4ADE80]': status === 'ONLINE',
        'bg-slate-500': status === 'OFFLINE',
        'bg-[#EF4444]': status === 'FAULT',
      }"></span>
      <span class="text-white">{{ cameraName }}</span>
    </div>

    <!-- Bottom info bar -->
    <div class="absolute bottom-0 left-0 right-0 px-3 py-2 bg-gradient-to-t from-black/80 to-transparent text-[10px] font-mono flex items-center justify-between">
      <div class="flex items-center gap-3">
        <span class="text-[#EF4444]" v-if="diseaseCount > 0">病 {{ diseaseCount }}</span>
        <span class="text-[#4488ff]" v-if="pestCount > 0">虫 {{ pestCount }}</span>
        <span class="text-[#4ADE80]" v-if="diseaseCount === 0 && pestCount === 0 && status === 'ONLINE'">健康</span>
      </div>
      <span class="text-slate-500" v-if="lastDetectTime">{{ lastDetectTime.slice(11, 19) }}</span>
    </div>

    <!-- Offline placeholder -->
    <div v-if="status === 'OFFLINE' && !streamUrl" class="absolute inset-0 flex flex-col items-center justify-center text-slate-600">
      <svg class="w-10 h-10 mb-2" fill="none" stroke="currentColor" stroke-width="1" viewBox="0 0 24 24">
        <path d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z" />
      </svg>
      <span class="text-xs">未配置视频流</span>
    </div>

    <!-- FAULT indicator -->
    <div v-if="status === 'FAULT'" class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 px-3 py-2 rounded-lg bg-[#EF4444]/20 border border-[#EF4444]/30 text-[#EF4444] text-xs font-mono">
      连接异常
    </div>
  </div>
</template>

<style scoped>
.camera-window {
  position: relative;
  width: 100%;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
</style>

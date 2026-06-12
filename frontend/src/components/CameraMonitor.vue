<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import Hls from 'hls.js'
import type { Subscription } from '@stomp/stompjs'
import { connectWs, disconnectWs, subscribeTopic, type DetectionItem, type InferenceResultMessage } from '../utils/websocket'

const props = withDefaults(defineProps<{
  cameraId: string
  cameraName: string
  streamUrl?: string
  active?: boolean
}>(), {
  active: true,
})

const emit = defineEmits<{
  (e: 'status-change', status: string): void
}>()

const videoRef = ref<HTMLVideoElement>()
const canvasRef = ref<HTMLCanvasElement>()
const containerRef = ref<HTMLDivElement>()
const status = ref<'ONLINE' | 'OFFLINE' | 'FAULT'>('OFFLINE')
const diseaseCount = ref(0)
const pestCount = ref(0)
const lastDetectTime = ref('')

let hls: Hls | null = null
let subscription: Subscription | null = null

function initHls() {
  if (!videoRef.value || !props.streamUrl) return

  if (Hls.isSupported()) {
    hls = new Hls({
      liveSyncDurationCount: 1,
      liveMaxLatencyDurationCount: 3,
      maxBufferLength: 5,
    })
    hls.loadSource(props.streamUrl)
    hls.attachMedia(videoRef.value)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      videoRef.value?.play()
      status.value = 'ONLINE'
      emit('status-change', 'ONLINE')
    })
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        status.value = 'FAULT'
        emit('status-change', 'FAULT')
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          hls?.startLoad()
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls?.recoverMediaError()
        }
      }
    })
  } else if (videoRef.value.canPlayType('application/vnd.apple.mpegurl')) {
    videoRef.value.src = props.streamUrl
    videoRef.value.addEventListener('loadedmetadata', () => {
      videoRef.value?.play()
      status.value = 'ONLINE'
      emit('status-change', 'ONLINE')
    })
  }
}

function syncCanvasSize() {
  if (!videoRef.value || !canvasRef.value) return
  const rect = videoRef.value.getBoundingClientRect()
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

  const scaleX = canvas.width / frameWidth
  const scaleY = canvas.height / frameHeight

  detections.forEach(det => {
    const x = det.bbox.x * scaleX
    const y = det.bbox.y * scaleY
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

async function startMonitoring() {
  if (!props.active || !props.streamUrl) return
  initHls()
  try {
    await connectWs()
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
  hls?.destroy()
  hls = null
  disconnectWs()
  clearCanvas()
  status.value = 'OFFLINE'
}

watch(() => props.active, (val) => {
  if (val) startMonitoring()
  else stopMonitoring()
})

watch(() => props.streamUrl, (val) => {
  if (val && props.active) {
    hls?.destroy()
    initHls()
  }
})

onMounted(() => {
  if (props.active) startMonitoring()
})

onUnmounted(() => {
  stopMonitoring()
})
</script>

<template>
  <div ref="containerRef" class="camera-window group">
    <video ref="videoRef" autoplay muted playsinline class="absolute inset-0 w-full h-full object-contain bg-black"></video>
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
  aspect-ratio: 4 / 3;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
</style>

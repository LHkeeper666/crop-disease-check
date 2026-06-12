<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import CameraMonitor from '../components/CameraMonitor.vue'

interface CameraItem {
  id: string
  name: string
  status: string
  streamUrl?: string
  grid?: string
}

const cameras = ref<CameraItem[]>([])
const selectedCamera = ref<CameraItem | null>(null)
const columns = ref(2)
const isLoading = ref(true)
const fetchError = ref('')

async function fetchCameras() {
  isLoading.value = true
  fetchError.value = ''
  try {
    const token = localStorage.getItem('treeforge_token')
    const res = await fetch('/api/camera/list', {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!res.ok) throw new Error('加载摄像头列表失败')
    const data = await res.json()
    if (data.code !== 200) throw new Error(data.message || '加载失败')
    const records = data.data?.records || []
    cameras.value = records.map((c: any) => ({
      id: c.id,
      name: c.name,
      status: c.status || 'OFFLINE',
      streamUrl: c.status === 'ONLINE' ? `/api/stream/${c.id}.m3u8` : undefined,
      grid: c.coverageGrids?.join(', ') || '',
    }))
    if (cameras.value.length > 0 && !selectedCamera.value) {
      selectedCamera.value = cameras.value[0]
    }
  } catch (e: any) {
    fetchError.value = e.message || '加载摄像头失败'
  } finally {
    isLoading.value = false
  }
}

function selectCamera(cam: CameraItem) {
  selectedCamera.value = cam
}

function handleStatusChange(cameraId: string, newStatus: string) {
  const cam = cameras.value.find(c => c.id === cameraId)
  if (cam) cam.status = newStatus
}

let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  fetchCameras()
  // 每10秒刷新摄像头状态，自动发现重连成功的摄像头
  refreshTimer = setInterval(fetchCameras, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">实时监测</h1>
        <p class="text-xs text-slate-500 font-mono">LIVE CAMERA MONITORING</p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Column selector -->
        <div class="flex items-center gap-2">
          <span class="text-[10px] text-slate-500 font-mono">布局</span>
          <button
            v-for="n in [1, 2, 3]"
            :key="n"
            class="w-7 h-7 rounded-lg text-xs font-mono transition-all"
            :class="columns === n
              ? 'bg-[#FF6A00]/20 border border-[#FF6A00]/30 text-[#FF6A00]'
              : 'bg-white/5 border border-white/5 text-slate-500 hover:text-white'"
            @click="columns = n"
          >
            {{ n }}
          </button>
        </div>
        <button
          class="px-3 py-1.5 rounded-lg text-xs font-mono bg-white/5 border border-white/5 text-slate-400 hover:text-white transition-all"
          @click="fetchCameras"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- Error -->
    <div v-if="fetchError" class="px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs shrink-0">
      {{ fetchError }}
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="text-slate-500 text-sm">加载摄像头列表中...</div>
    </div>

    <!-- Empty -->
    <div v-else-if="cameras.length === 0" class="flex-1 flex flex-col items-center justify-center">
      <svg class="w-16 h-16 text-slate-700 mb-4" fill="none" stroke="currentColor" stroke-width="1" viewBox="0 0 24 24">
        <path d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z" />
      </svg>
      <p class="text-sm text-slate-500">暂无摄像头设备</p>
      <p class="text-[10px] text-slate-600 mt-1">请先在设备管理中添加摄像头</p>
    </div>

    <!-- Camera grid + detail — ratio 78% | 22% -->
    <div v-else class="flex-1 min-h-0 overflow-hidden" style="display: grid; grid-template-columns: 78fr 22fr; gap: 1rem;">
      <!-- Grid view -->
      <div class="min-w-0 overflow-y-auto">
        <div
          :style="{
            display: 'grid',
            gridTemplateColumns: `repeat(${columns}, 1fr)`,
            gap: '8px',
          }"
        >
          <div
            v-for="cam in cameras"
            :key="cam.id"
            class="cursor-pointer rounded-lg transition-all"
            :class="selectedCamera?.id === cam.id
              ? 'ring-2 ring-[#FF6A00]/50'
              : 'ring-1 ring-white/5 hover:ring-white/20'"
            @click="selectCamera(cam)"
          >
            <CameraMonitor
              :camera-id="cam.id"
              :camera-name="cam.name"
              :stream-url="cam.streamUrl"
              :active="cam.status !== 'OFFLINE'"
              @status-change="(s) => handleStatusChange(cam.id, s)"
            />
          </div>
        </div>
      </div>

      <!-- Detail sidebar -->
      <GlassCard v-if="selectedCamera" class="min-w-0 flex flex-col">
        <div class="text-xs text-slate-400 tracking-wider mb-3">摄像头详情</div>
        <div class="space-y-3 flex-1 overflow-y-auto">
          <div>
            <div class="text-[10px] text-slate-600 font-mono mb-1">名称</div>
            <div class="text-sm text-white">{{ selectedCamera.name }}</div>
          </div>
          <div>
            <div class="text-[10px] text-slate-600 font-mono mb-1">状态</div>
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full" :class="{
                'bg-[#4ADE80]': selectedCamera.status === 'ONLINE',
                'bg-slate-500': selectedCamera.status === 'OFFLINE',
                'bg-[#EF4444]': selectedCamera.status === 'FAULT',
              }"></span>
              <span class="text-sm text-white">{{ selectedCamera.status }}</span>
            </div>
          </div>
          <div v-if="selectedCamera.grid">
            <div class="text-[10px] text-slate-600 font-mono mb-1">覆盖网格</div>
            <div class="text-sm text-white">{{ selectedCamera.grid }}</div>
          </div>
          <div>
            <div class="text-[10px] text-slate-600 font-mono mb-1">摄像头ID</div>
            <div class="text-[10px] text-slate-500 font-mono break-all">{{ selectedCamera.id }}</div>
          </div>
        </div>

        <!-- Camera list -->
        <div class="mt-3 pt-3 border-t border-white/5">
          <div class="text-[10px] text-slate-600 font-mono mb-2">设备列表</div>
          <div class="space-y-1 max-h-48 overflow-y-auto">
            <button
              v-for="cam in cameras"
              :key="cam.id"
              class="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg text-xs transition-all"
              :class="selectedCamera?.id === cam.id
                ? 'bg-white/10 text-white'
                : 'text-slate-400 hover:text-white hover:bg-white/5'"
              @click="selectCamera(cam)"
            >
              <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="{
                'bg-[#4ADE80]': cam.status === 'ONLINE',
                'bg-slate-500': cam.status === 'OFFLINE',
                'bg-[#EF4444]': cam.status === 'FAULT',
              }"></span>
              <span class="truncate">{{ cam.name }}</span>
            </button>
          </div>
        </div>
      </GlassCard>
    </div>
  </div>
</template>

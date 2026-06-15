<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import CameraMonitor from '../components/CameraMonitor.vue'
import type { DetectionItem } from '../utils/websocket'

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

// 每个摄像头的最新检测结果
const detectionsMap = ref<Record<string, DetectionItem[]>>({})

const selectedDetections = computed(() => {
  if (!selectedCamera.value) return []
  return detectionsMap.value[selectedCamera.value.id] || []
})

const selectedDiseases = computed(() =>
  selectedDetections.value.filter(d => d.type === 'disease')
)

const selectedPests = computed(() =>
  selectedDetections.value.filter(d => d.type === 'pest')
)

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
      streamUrl: c.httpUrl || undefined,
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

function handleDetections(cameraId: string, items: DetectionItem[]) {
  detectionsMap.value[cameraId] = items
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
              @detections="(items) => handleDetections(cam.id, items)"
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

          <!-- 检测结果 -->
          <div class="pt-3 border-t border-white/5">
            <div class="text-[10px] text-slate-600 font-mono mb-2">抽帧检测结果</div>

            <!-- 无检测结果 -->
            <div v-if="selectedDetections.length === 0" class="text-[10px] text-slate-600">
              暂无检测数据
            </div>

            <!-- 病害列表 -->
            <div v-if="selectedDiseases.length > 0" class="mb-2">
              <div class="text-[10px] text-[#EF4444] font-mono mb-1">病害 ({{ selectedDiseases.length }})</div>
              <div class="space-y-1">
                <div
                  v-for="(det, i) in selectedDiseases"
                  :key="'d-' + i"
                  class="flex items-center justify-between px-2 py-1 rounded bg-[#EF4444]/5 border border-[#EF4444]/10"
                >
                  <span class="text-xs text-white truncate">{{ det.nameCn }}</span>
                  <span class="text-[10px] text-[#EF4444] font-mono ml-2 shrink-0">
                    {{ (det.confidence * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </div>

            <!-- 虫害列表 -->
            <div v-if="selectedPests.length > 0" class="mb-2">
              <div class="text-[10px] text-[#4488ff] font-mono mb-1">虫害 ({{ selectedPests.length }})</div>
              <div class="space-y-1">
                <div
                  v-for="(det, i) in selectedPests"
                  :key="'p-' + i"
                  class="flex items-center justify-between px-2 py-1 rounded bg-[#4488ff]/5 border border-[#4488ff]/10"
                >
                  <span class="text-xs text-white truncate">{{ det.nameCn }}</span>
                  <span class="text-[10px] text-[#4488ff] font-mono ml-2 shrink-0">
                    {{ (det.confidence * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </div>

            <!-- 无病虫害 -->
            <div
              v-if="selectedDiseases.length === 0 && selectedPests.length === 0 && selectedDetections.length > 0"
              class="text-[10px] text-[#4ADE80]"
            >
              未发现病虫害
            </div>
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
              <!-- 检测数量角标 -->
              <span
                v-if="detectionsMap[cam.id]?.length"
                class="ml-auto text-[9px] px-1.5 py-0.5 rounded font-mono"
                :class="detectionsMap[cam.id].some(d => d.type === 'disease')
                  ? 'bg-[#EF4444]/15 text-[#EF4444]'
                  : 'bg-[#4488ff]/15 text-[#4488ff]'"
              >
                {{ detectionsMap[cam.id].length }}
              </span>
            </button>
          </div>
        </div>
      </GlassCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import GlassCard from '../components/GlassCard.vue'
import DataMetric from '../components/DataMetric.vue'
import { useWorkOrderStore } from '../stores/workorder'
import { useDashboardSettingsStore } from '../stores/dashboardSettings'
import { useAuthStore } from '../stores/auth'
import { fetchCameras, type CameraVO } from '../api/camera'
import { fetchStatisticsOverview, type GridHeatmapItem, type DailyTrend } from '../api/statistics'
import { fetchGrids, updateGrid, type GridVO } from '../api/grid'
import { connectWs, disconnectWs, subscribeTopic } from '../utils/websocket'

const auth = useAuthStore()
const isAdmin = computed(() => auth.userRole === 'ADMIN')
const dashSettings = useDashboardSettingsStore()
const env = dashSettings.env
const cameras = ref<CameraVO[]>([])
const gridHeatmap = ref<GridHeatmapItem[]>([])
const dailyTrend = ref<DailyTrend[]>([])
const woStore = useWorkOrderStore()
const router = useRouter()

// 实时探测 & 监控状态
const monitoredCameraIds = ref<Set<string>>(new Set())
let probeTimer: ReturnType<typeof setInterval> | null = null
let wsCallerId: string | null = null
let heatmapSub: any = null
let workorderSub: any = null

function handleHeatmapUpdate(data: { gridLabel: string; score: number }) {
  const idx = gridHeatmap.value.findIndex(h => h.gridLabel === data.gridLabel)
  if (idx !== -1) {
    gridHeatmap.value[idx] = { ...gridHeatmap.value[idx], score: data.score }
  }
}

async function handleWorkorderChange(data: {
  workorderId: number; newStatus: string; type?: string; severity?: string
}) {
  const existing = woStore.orders.find(o => o.id === data.workorderId)
  if (existing) {
    // 已有工单：本地更新 status/severity → 触发 gridSeverityMap 重算
    woStore.updateOrderLocal(data.workorderId, { status: data.newStatus as any, severity: (data.severity as any) || existing.severity })
  } else {
    // 新工单：刷新全量工单列表（比单独 fetch detail 更可靠）
    woStore.fetchOrders()
  }
}

/** 从前端网络探测摄像头 RTSP 端口可达性 */
function parseRtspHostPort(rtspUrl: string): { host: string; port: number } | null {
  try {
    const url = new URL(rtspUrl)
    return { host: url.hostname, port: parseInt(url.port) || 554 }
  } catch { return null }
}

async function probeCameraReachability(cam: CameraVO): Promise<string> {
  if (!cam.rtspUrl) return 'OFFLINE'
  const parsed = parseRtspHostPort(cam.rtspUrl)
  if (!parsed) return 'OFFLINE'
  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 3000)
    await fetch(`http://${parsed.host}:${parsed.port}/`, { mode: 'no-cors', signal: controller.signal })
    clearTimeout(timer)
    return 'ONLINE'
  } catch {
    return 'OFFLINE'
  }
}

async function probeAllCameras() {
  await Promise.allSettled(
    cameras.value.map(async cam => {
      const probed = await probeCameraReachability(cam)
      const prevStatus = cam.status
      cam.status = probed as CameraVO['status']
      if (prevStatus !== 'ONLINE' && probed === 'ONLINE') {
        startCameraMonitor(cam)
      }
    })
  )
}

function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token')
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

function startCameraMonitor(cam: CameraVO) {
  if (monitoredCameraIds.value.has(cam.id)) return
  fetch(`/api/camera/${cam.id}/monitor`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ enabled: true, intervalSeconds: 5, confidence: 0.5 }),
  }).then(() => {
    monitoredCameraIds.value.add(cam.id)
  }).catch(e => console.warn('[Dashboard] 启动监测失败:', cam.id, e))
}

function stopAllMonitors() {
  for (const id of monitoredCameraIds.value) {
    fetch(`/api/camera/${id}/monitor`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ enabled: false }),
    }).catch(() => {})
  }
  monitoredCameraIds.value.clear()
}

async function initRealtimeWs() {
  try {
    wsCallerId = await connectWs()
    heatmapSub = subscribeTopic('/topic/heatmap-update', (msg: any) => {
      if (msg.data) handleHeatmapUpdate(msg.data)
    })
    workorderSub = subscribeTopic('/topic/workorder-change', (msg: any) => {
      if (msg.data) handleWorkorderChange(msg.data)
    })
  } catch (e: any) {
    console.warn('[Dashboard] WebSocket 连接失败:', e?.message)
    // Token 过期 → 跳转登录页
    if (e?.message?.includes('Token') || e?.message?.includes('认证')) {
      auth.logout()
    }
  }
}

function navigateToMonitor(cameraId: string) {
  router.push({ name: 'Monitor', query: { cameraId } })
}

// Editable metadata (persisted)
const meta = dashSettings.meta
// Double-click editing for metadata
const editingField = ref<string | null>(null)
function startEditMeta(field: string) {
  editingField.value = field
}
function stopEditMeta() {
  editingField.value = null
}

function updateGrowthValue(idx: number, text: string) {
  const num = parseFloat(text)
  if (!isNaN(num)) {
    dashSettings.growth[idx].value = num
  }
}

// 从数据库读取网格列表
const dbGrids = ref<GridVO[]>([])

// 合并数据库网格 + 统计热力图 + 工单数据，动态生成网格单元
const gridCells = computed(() => {
  // 以数据库网格为主，如果没有则用热力图数据
  if (dbGrids.value.length > 0) {
    return dbGrids.value.map(g => {
      const heatmap = gridHeatmap.value.find(h => h.gridLabel === g.label)
      const severity = woStore.gridSeverityMap[g.label]
      const activeOrder = woStore.orders.find(o => o.gridLabel === g.label && (o.status === 'PENDING' || o.status === 'PROCESSING'))
      return {
        id: g.id,
        label: g.label,
        severity: severity || null,
        pest: activeOrder?.pestName || '',
        score: heatmap?.score ?? 0,
        cropType: g.cropType || '',
      }
    })
  }
  // 回退：用热力图数据
  if (gridHeatmap.value.length > 0) {
    return gridHeatmap.value.map(h => {
      const severity = woStore.gridSeverityMap[h.gridLabel]
      const activeOrder = woStore.orders.find(o => o.gridLabel === h.gridLabel && (o.status === 'PENDING' || o.status === 'PROCESSING'))
      return {
        id: h.gridId,
        label: h.gridLabel,
        severity: severity || null,
        pest: activeOrder?.pestName || '',
        score: h.score,
        cropType: '',
      }
    })
  }
  return []
})

// 热力图颜色：5种
const severityColor: Record<string, string> = {
  CRITICAL: 'bg-sakura/60',
  HIGH: 'bg-orange-500/50',
  MEDIUM: 'bg-amber/40',
  LOW: 'bg-blue-400/30',
}

function getCellColor(cell: { severity: string | null }) {
  if (!cell.severity) return 'bg-cyber-green/20'
  return severityColor[cell.severity] || 'bg-cyber-green/20'
}

function getCellLabel(severity: string | null) {
  if (!severity) return '安全'
  const map: Record<string, string> = { CRITICAL: '紧急', HIGH: '高危', MEDIUM: '中等', LOW: '低' }
  return map[severity] || '安全'
}

function getCellLabelColor(severity: string | null) {
  if (!severity) return 'text-cyber-green'
  const map: Record<string, string> = { CRITICAL: 'text-sakura', HIGH: 'text-orange-400', MEDIUM: 'text-amber', LOW: 'text-blue-400' }
  return map[severity] || 'text-cyber-green'
}

// Detection confidence threshold (0-1), will be sent to backend
const confidenceThreshold = ref(0)

// 报警列表（受检测阈值过滤）
const alerts = computed(() => woStore.getAlerts(confidenceThreshold.value))

// Heatmap cell detail
const selectedCell = ref<{ id: string; label: string; severity: string | null; pest: string; score: number; cropType?: string } | null>(null)

function selectCell(cell: { id: string; label: string; severity: string | null; pest: string; score: number; cropType?: string }) {
  selectedCell.value = cell
  editingCropType.value = false
  cropTypeInput.value = cell.cropType || ''
}

function closeCellDetail() {
  selectedCell.value = null
  editingCropType.value = false
  stopAutoRotation()
  startAutoRotation()
}

// Crop type editing
const editingCropType = ref(false)
const cropTypeInput = ref('')
const cropTypeSaving = ref(false)

function startEditCropType() {
  cropTypeInput.value = selectedCell.value?.cropType || ''
  editingCropType.value = true
}

async function saveCropType() {
  if (!selectedCell.value || !cropTypeInput.value.trim()) return
  cropTypeSaving.value = true
  try {
    await updateGrid(selectedCell.value.id, { cropType: cropTypeInput.value.trim() })
    selectedCell.value.cropType = cropTypeInput.value.trim()
    // 同步更新本地网格列表
    const idx = dbGrids.value.findIndex(g => g.id === selectedCell.value!.id)
    if (idx !== -1) dbGrids.value[idx].cropType = cropTypeInput.value.trim()
    editingCropType.value = false
  } catch (e: any) {
    console.error('[Dashboard] 更新作物类型失败:', e.message)
  } finally {
    cropTypeSaving.value = false
  }
}

// Real-time clock
const now = ref(new Date())
let clockTimer: ReturnType<typeof setInterval>
onMounted(() => {
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  // 加载工单数据（供热力图、报警面板、趋势图使用）
  woStore.fetchOrders()
  // 加载摄像头列表，完成后启动实时监测与状态探测
  fetchCameras({ size: 50 }).then(page => {
    cameras.value = page.records
    // 对在线摄像头启动后端持续监测（触发推理 → 工单 → 热力图更新）
    cameras.value.filter(c => c.status === 'ONLINE').forEach(startCameraMonitor)
    // 前端网络可达性探测（每 5 秒）
    probeAllCameras()
    probeTimer = setInterval(() => probeAllCameras(), 5000)
  }).catch(e => console.error('[Dashboard] 加载摄像头失败:', e.message))
  // 加载网格列表（从数据库）
  fetchGrids().then(list => {
    dbGrids.value = list
  }).catch(e => console.error('[Dashboard] 加载网格失败:', e.message))
  // 加载统计数据（热力图 + 7日趋势，作为基线）
  fetchStatisticsOverview().then(ov => {
    gridHeatmap.value = ov.gridHeatmap || []
    dailyTrend.value = ov.dailyTrend || []
    renderTrendChart()
  }).catch(e => console.error('[Dashboard] 加载统计数据失败:', e.message))
  // WebSocket 实时推送：热力图分数 + 工单变更
  initRealtimeWs()
})
onUnmounted(() => {
  clearInterval(clockTimer)
  if (probeTimer) { clearInterval(probeTimer); probeTimer = null }
  stopAllMonitors()
  heatmapSub?.unsubscribe()
  workorderSub?.unsubscribe()
  disconnectWs(wsCallerId ?? undefined)
})

// 2.5D Heatmap drag-to-rotate
const heatmapRef = ref<HTMLDivElement>()
const heatmapRotate = reactive({ x: 15, z: -2 })
const isDragging = ref(false)
const isAutoRotating = ref(true)
let dragStart = { x: 0, y: 0 }
let rotateStart = { x: 0, z: 0 }
let autoRotateTimer: ReturnType<typeof setInterval> | null = null
let lastFrameTime = 0

function startAutoRotation() {
  if (autoRotateTimer) return
  isAutoRotating.value = true
  lastFrameTime = Date.now()
  const speed = 360 / 30000 // 360° per 30 seconds

  autoRotateTimer = setInterval(() => {
    if (isDragging.value) { lastFrameTime = Date.now(); return }
    const now = Date.now()
    const delta = now - lastFrameTime
    lastFrameTime = now
    heatmapRotate.z += speed * delta
  }, 16) // ~60fps
}

function stopAutoRotation() {
  if (autoRotateTimer) {
    clearInterval(autoRotateTimer)
    autoRotateTimer = null
  }
  isAutoRotating.value = false
}

function resumeAutoRotation() {
  if (autoRotateTimer) return
  startAutoRotation()
}

function onDragStart(e: MouseEvent) {
  isDragging.value = true
  dragStart = { x: e.clientX, y: e.clientY }
  rotateStart = { x: heatmapRotate.x, z: heatmapRotate.z }
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  const dx = e.clientX - dragStart.x
  const dy = e.clientY - dragStart.y
  heatmapRotate.z = rotateStart.z + dx * 0.3
  heatmapRotate.x = Math.max(-30, Math.min(60, rotateStart.x - dy * 0.3))
}

function onDragEnd() {
  isDragging.value = false
  // Resume continuous rotation from current angle after a brief pause
  setTimeout(() => resumeAutoRotation(), 2000)
}

function resetRotation() {
  heatmapRotate.x = 15
  heatmapRotate.z = -2
  stopAutoRotation()
  startAutoRotation()
}

onMounted(() => {
  heatmapRef.value?.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
  startAutoRotation()
})

onUnmounted(() => {
  heatmapRef.value?.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
  stopAutoRotation()
})

// ECharts — trend chart (from statistics API, same as Reports)
const trendChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null

function renderTrendChart() {
  if (!trendChartRef.value || dailyTrend.value.length === 0) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const data = {
    dates: dailyTrend.value.map(d => d.date.slice(5)),
    disease: dailyTrend.value.map(d => d.diseaseCount),
    pest: dailyTrend.value.map(d => d.pestCount),
  }
  trendChart.setOption({
    backgroundColor: 'transparent',
    grid: { top: 20, right: 15, bottom: 25, left: 40 },
    xAxis: {
      type: 'category',
      data: data.dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      axisLabel: { color: 'rgba(255,255,255,0.4)', fontSize: 10, fontFamily: 'JetBrains Mono' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: { color: 'rgba(255,255,255,0.3)', fontSize: 10, fontFamily: 'JetBrains Mono' },
      minInterval: 1,
    },
    series: [
      {
        name: '病害',
        type: 'bar',
        stack: 'total',
        data: data.disease,
        itemStyle: { color: '#EF4444', borderRadius: [0, 0, 0, 0] },
        barWidth: 16,
      },
      {
        name: '虫害',
        type: 'bar',
        stack: 'total',
        data: data.pest,
        itemStyle: { color: '#FFB300', borderRadius: [2, 2, 0, 0] },
        barWidth: 16,
      },
    ],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15,23,42,0.9)',
      borderColor: 'rgba(255,255,255,0.1)',
      textStyle: { color: '#e2e8f0', fontSize: 12 },
    },
  })
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">2.5D 植物生理遥测舱</h1>
        <p class="text-xs text-slate-500 font-mono">实时植物生理遥测</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-cyber-green/10 border border-cyber-green/20">
          <span class="w-2 h-2 rounded-full bg-cyber-green pulse-green" />
          <span class="text-xs text-cyber-green font-mono">在线</span>
        </div>
        <div class="text-xs text-slate-500 font-mono">
          {{ now.toLocaleString('zh-CN') }}
        </div>
      </div>
    </div>

    <!-- 3-column layout: fixed ratio 19% | 50% | 31% -->
    <div class="flex-1 min-h-0 overflow-hidden" style="display: grid; grid-template-columns: 19fr 50fr 31fr; gap: 1rem;">
      <!-- LEFT PANEL -->
      <div class="flex flex-col gap-3 min-w-0 overflow-y-auto">
        <!-- Environmental Grid 2x2 -->
        <div class="grid grid-cols-2 gap-2 border-glow-animated rounded-xl p-2">
          <DataMetric label="空气温度" :value="env.airTemp.value" unit="°C" :status="env.airTemp.status" :editable="isAdmin" @update:value="env.airTemp.value = $event" />
          <DataMetric label="土壤湿度" :value="env.soilMoisture.value" unit="%" :status="env.soilMoisture.status" :editable="isAdmin" @update:value="env.soilMoisture.value = $event" />
          <DataMetric label="空气湿度" :value="env.humidity.value" unit="%" :status="env.humidity.status" :editable="isAdmin" @update:value="env.humidity.value = $event" />
          <DataMetric label="光照强度" :value="env.lightLevel.value" unit="lux" :status="env.lightLevel.status" :editable="isAdmin" @update:value="env.lightLevel.value = $event" />
        </div>

        <!-- Confidence Threshold -->
        <GlassCard class="shrink-0">
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs text-slate-400 tracking-wider">检测阈值</span>
            <span class="text-sm font-mono font-bold" :class="confidenceThreshold >= 0.7 ? 'text-cyber-green' : confidenceThreshold >= 0.4 ? 'text-amber' : 'text-sakura'">
              {{ (confidenceThreshold * 100).toFixed(0) }}%
            </span>
          </div>
          <div class="slider-wrapper relative h-7 flex items-center">
            <div class="absolute inset-x-0 top-1/2 -translate-y-1/2 h-2 rounded-full bg-white/5 pointer-events-none" />
            <div
              class="absolute left-0 top-1/2 -translate-y-1/2 h-2 rounded-full pointer-events-none"
              :class="confidenceThreshold >= 0.7 ? 'bg-cyber-green/60' : confidenceThreshold >= 0.4 ? 'bg-amber/60' : 'bg-sakura/60'"
              :style="{ width: `${confidenceThreshold * 100}%` }"
            />
            <input
              v-model.number="confidenceThreshold"
              type="range"
              min="0"
              max="1"
              step="0.01"
              class="threshold-slider relative z-10 w-full"
            />
          </div>
          <div class="flex justify-between text-[10px] text-slate-600 font-mono">
            <span>0%</span>
            <span class="text-slate-500">低阈值 = 高灵敏</span>
            <span>100%</span>
          </div>
        </GlassCard>

        <!-- Alerts -->
        <GlassCard class="flex-1 min-h-0 flex flex-col">
          <div class="flex items-center justify-between mb-3 shrink-0">
            <span class="text-xs text-slate-400 tracking-wider">报警</span>
            <span class="text-[10px] font-mono text-sakura">{{ alerts.filter(a => a.severity === 'CRITICAL').length }} 严重</span>
          </div>
          <div class="space-y-2 overflow-y-auto flex-1 min-h-0">
            <div
              v-for="alert in alerts"
              :key="alert.id"
              class="px-3 py-2 rounded-lg border transition-all"
              :class="{
                'bg-sakura/5 border-sakura/20 glow-red': alert.severity === 'CRITICAL',
                'bg-orange-500/5 border-orange-500/20 glow-amber': alert.severity === 'HIGH',
                'bg-amber/5 border-amber/20': alert.severity === 'MEDIUM',
                'bg-blue-400/5 border-blue-400/20': alert.severity === 'LOW',
              }"
            >
              <div class="text-xs text-white leading-relaxed">{{ alert.message }}</div>
              <div class="text-[10px] text-slate-600 font-mono mt-1">{{ alert.time }}</div>
            </div>
          </div>
        </GlassCard>
      </div>

      <!-- CENTER PANEL -->
      <div class="flex flex-col gap-3 min-w-0 overflow-hidden">
        <!-- 2.5D Heatmap -->
        <GlassCard class="flex-1 min-h-0 flex flex-col">
          <div class="flex items-center justify-between mb-4">
            <span class="text-xs text-slate-400 tracking-wider">2.5D 空间热力图</span>
            <div class="flex gap-2">
              <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-cyber-green/10 text-cyber-green border border-cyber-green/20">全部</button>
              <button
                class="px-3 py-1 rounded-lg text-[10px] font-mono bg-white/5 text-slate-500 border border-white/10 hover:bg-white/10"
                @click="resetRotation"
              >重置</button>
            </div>
          </div>
          <div
            ref="heatmapRef"
            class="flex-1 flex items-center justify-center select-none"
            :class="isDragging ? 'cursor-grabbing' : 'cursor-grab'"
            @mousedown.prevent="onDragStart"
          >
            <div
              class="grid grid-cols-3 gap-3 w-full max-w-lg"
              :style="{ transform: `perspective(800px) rotateX(${heatmapRotate.x}deg) rotateZ(${heatmapRotate.z}deg)`, transition: isDragging ? 'none' : 'transform 0.4s ease' }"
            >
              <div
                v-for="cell in gridCells"
                :key="cell.label"
                class="aspect-square rounded-xl border border-white/10 flex flex-col items-center justify-center cursor-pointer transition-all duration-300 hover:scale-105 hover:border-white/25"
                :class="getCellColor(cell)"
                @click="selectCell(cell)"
              >
                <span class="text-sm font-mono font-bold text-white">{{ cell.label }}</span>
                <span class="text-[10px] font-mono mt-0.5" :class="getCellLabelColor(cell.severity)">{{ getCellLabel(cell.severity) }}</span>
              </div>
            </div>
          </div>
          <!-- Legend -->
          <div class="flex items-center justify-center gap-4 mt-3">
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-cyber-green/20" />
              <span class="text-[10px] text-slate-500">安全</span>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-blue-400/30" />
              <span class="text-[10px] text-slate-500">低</span>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-amber/40" />
              <span class="text-[10px] text-slate-500">中等</span>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-orange-500/50" />
              <span class="text-[10px] text-slate-500">高危</span>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-sakura/60" />
              <span class="text-[10px] text-slate-500">紧急</span>
            </div>
          </div>
        </GlassCard>

        <!-- Metadata Matrix — admin double-click to edit, auto-persists -->
        <div class="grid grid-cols-5 gap-2 shrink-0">
          <!-- 区域 -->
          <div class="glass rounded-lg px-3 py-2 text-center" :class="isAdmin ? 'cursor-pointer hover:border-white/20 transition-colors' : ''" @dblclick="isAdmin && startEditMeta('sectorId')">
            <div class="text-[10px] text-slate-500 mb-0.5">区域</div>
            <input v-if="editingField === 'sectorId'" v-model="meta.sectorId" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" @blur="stopEditMeta()" @keyup.enter="stopEditMeta()" autofocus />
            <div v-else class="text-xs font-mono text-white truncate">{{ meta.sectorId }}</div>
          </div>
          <!-- 作物 -->
          <div class="glass rounded-lg px-3 py-2 text-center" :class="isAdmin ? 'cursor-pointer hover:border-white/20 transition-colors' : ''" @dblclick="isAdmin && startEditMeta('cropSpecies')">
            <div class="text-[10px] text-slate-500 mb-0.5">作物</div>
            <input v-if="editingField === 'cropSpecies'" v-model="meta.cropSpecies" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" @blur="stopEditMeta()" @keyup.enter="stopEditMeta()" autofocus />
            <div v-else class="text-xs font-mono text-white truncate">{{ meta.cropSpecies.split(' ')[0] }}</div>
          </div>
          <!-- 定植日期 -->
          <div class="glass rounded-lg px-3 py-2 text-center" :class="isAdmin ? 'cursor-pointer hover:border-white/20 transition-colors' : ''" @dblclick="isAdmin && startEditMeta('plantingDate')">
            <div class="text-[10px] text-slate-500 mb-0.5">定植日期</div>
            <input v-if="editingField === 'plantingDate'" v-model="meta.plantingDate" type="date" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" @blur="stopEditMeta()" @keyup.enter="stopEditMeta()" autofocus />
            <div v-else class="text-xs font-mono text-white">{{ meta.plantingDate }}</div>
          </div>
          <!-- 位置 -->
          <div class="glass rounded-lg px-3 py-2 text-center" :class="isAdmin ? 'cursor-pointer hover:border-white/20 transition-colors' : ''" @dblclick="isAdmin && startEditMeta('location')">
            <div class="text-[10px] text-slate-500 mb-0.5">位置</div>
            <input v-if="editingField === 'location'" v-model="meta.location" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" @blur="stopEditMeta()" @keyup.enter="stopEditMeta()" autofocus />
            <div v-else class="text-[10px] font-mono text-white leading-tight">{{ meta.location }}</div>
          </div>
          <!-- 面积 -->
          <div class="glass rounded-lg px-3 py-2 text-center" :class="isAdmin ? 'cursor-pointer hover:border-white/20 transition-colors' : ''" @dblclick="isAdmin && startEditMeta('area')">
            <div class="text-[10px] text-slate-500 mb-0.5">面积</div>
            <input v-if="editingField === 'area'" v-model="meta.area" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" @blur="stopEditMeta()" @keyup.enter="stopEditMeta()" autofocus />
            <div v-else class="text-xs font-mono text-white">{{ meta.area }}</div>
          </div>
        </div>
      </div>

      <!-- RIGHT PANEL -->
      <div class="flex flex-col gap-3 min-w-0 overflow-y-auto">
        <!-- Growth Metrics — double-click value to edit -->
        <GlassCard class="border-glow-animated">
          <div class="text-xs text-slate-400 tracking-wider mb-2">生长指标</div>
          <div class="space-y-1.5">
            <div v-for="(m, idx) in dashSettings.growth" :key="m.label" class="flex items-center gap-3">
              <span class="text-[10px] text-white w-14 shrink-0">{{ m.label }}</span>
              <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-1000"
                  :style="{ width: `${Math.min((m.value / m.max) * 100, 100)}%`, backgroundColor: m.color }"
                />
              </div>
              <span
                class="text-xs font-mono text-white w-20 text-right shrink-0"
                :class="isAdmin ? 'cursor-pointer hover:text-cyber-green transition-colors' : ''"
                @dblclick="isAdmin && (($event.target as HTMLElement).contentEditable = 'true', ($event.target as HTMLElement).focus())"
                @blur="($event.target as HTMLElement).contentEditable = 'false'; updateGrowthValue(idx, ($event.target as HTMLElement).textContent ?? '')"
                @keyup.enter="($event.target as HTMLElement).blur()"
              >{{ m.value }} {{ m.unit }}</span>
            </div>
          </div>
        </GlassCard>

        <!-- Trend Chart -->
        <GlassCard class="shrink-0">
          <div class="text-xs text-slate-400 tracking-wider mb-3">7日趋势</div>
          <div ref="trendChartRef" class="h-32" />
        </GlassCard>

        <!-- Camera Monitoring -->
        <GlassCard class="flex-1 min-h-0 flex flex-col">
          <div class="flex items-center justify-between mb-3 shrink-0">
            <span class="text-xs text-slate-400 tracking-wider">实时监控</span>
            <span class="text-[10px] font-mono text-cyber-green">{{ cameras.filter(c => c.status === 'ONLINE').length }}/{{ cameras.length }} 在线</span>
          </div>
          <div class="grid grid-cols-2 xl:grid-cols-3 gap-2 flex-1 min-h-0">
            <div
              v-for="camera in cameras"
              :key="camera.id"
              class="glass rounded-lg overflow-hidden flex flex-col cursor-pointer hover:ring-1 hover:ring-[#FF6A00]/30 transition-all"
              @click="navigateToMonitor(camera.id)"
            >
              <div class="flex-1 min-h-0 relative bg-slate-900/50 flex items-center justify-center">
                <template v-if="camera.status === 'ONLINE'">
                  <div class="absolute inset-0 bg-gradient-to-br from-slate-800/50 to-slate-900/50 flex items-center justify-center">
                    <div class="text-center">
                      <svg class="w-8 h-8 text-cyber-green/40 mx-auto mb-2" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                        <path d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z" />
                      </svg>
                      <div class="text-[10px] text-slate-500 font-mono">视频流</div>
                    </div>
                  </div>
                  <div class="absolute top-2 right-2 w-2 h-2 rounded-full bg-cyber-green pulse-green" />
                </template>
                <template v-else>
                  <div class="absolute inset-0 flex items-center justify-center">
                    <div class="text-center">
                      <svg class="w-8 h-8 mx-auto mb-2" :class="camera.status === 'OFFLINE' ? 'text-sakura/40' : 'text-amber/40'" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                        <path v-if="camera.status === 'OFFLINE'" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
                        <path v-else d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
                      </svg>
                      <div class="text-[10px] font-mono" :class="camera.status === 'OFFLINE' ? 'text-sakura' : 'text-amber'">
                        {{ camera.status === 'OFFLINE' ? '未连接' : '故障' }}
                      </div>
                    </div>
                  </div>
                  <!-- Status dot for non-online cameras -->
                  <div
                    class="absolute top-2 right-2 w-2 h-2 rounded-full"
                    :class="camera.status === 'OFFLINE' ? 'bg-sakura pulse-red' : 'bg-amber pulse-amber'"
                  />
                </template>
              </div>
              <div class="px-2 py-1.5 border-t border-white/5">
                <div class="text-[10px] text-white truncate">{{ camera.name }}</div>
                <div class="flex items-center justify-between">
                  <span class="text-[9px] text-slate-500 font-mono">{{ camera.captureResolution }}</span>
                  <span
                    class="text-[9px] font-mono"
                    :class="camera.status === 'ONLINE' ? 'text-cyber-green' : camera.status === 'OFFLINE' ? 'text-sakura' : 'text-amber'"
                  >
                    {{ camera.status === 'ONLINE' ? '在线' : camera.status === 'OFFLINE' ? '离线' : '故障' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>

    <!-- Cell detail modal -->
    <Teleport to="body">
      <div
        v-if="selectedCell"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @mousedown.self="closeCellDetail"
      >
        <div class="glass rounded-2xl p-6 w-80 shadow-2xl border border-white/10">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-xl flex items-center justify-center font-mono font-bold text-white text-lg" :class="getCellColor(selectedCell)">
                {{ selectedCell.label }}
              </div>
              <div>
                <div class="text-sm font-medium text-white">网格 {{ selectedCell.label }}</div>
                <div class="text-[10px] text-slate-500 font-mono">GRID DETAIL</div>
              </div>
            </div>
          </div>
          <div class="space-y-3">
            <div class="flex justify-between items-center py-2 border-b border-white/5">
              <span class="text-xs text-slate-400">威胁等级</span>
              <span class="text-sm font-mono font-bold" :class="getCellLabelColor(selectedCell.severity)">
                {{ getCellLabel(selectedCell.severity) }}
              </span>
            </div>
            <div class="flex justify-between items-center py-2 border-b border-white/5">
              <span class="text-xs text-slate-400">作物类型</span>
              <template v-if="editingCropType">
                <div class="flex items-center gap-2">
                  <input
                    v-model="cropTypeInput"
                    class="w-28 text-sm font-mono text-white bg-transparent border-b border-cyber-green/50 outline-none"
                    @keyup.enter="saveCropType()"
                    @keyup.escape="editingCropType = false"
                    autofocus
                  />
                  <button
                    class="text-[10px] px-2 py-0.5 rounded bg-cyber-green/20 text-cyber-green border border-cyber-green/30 hover:bg-cyber-green/30 disabled:opacity-50"
                    :disabled="cropTypeSaving || !cropTypeInput.trim()"
                    @click="saveCropType()"
                  >{{ cropTypeSaving ? '...' : '保存' }}</button>
                </div>
              </template>
              <template v-else>
                <span
                  class="text-sm font-mono"
                  :class="[
                    selectedCell.cropType ? 'text-white' : 'text-slate-600',
                    isAdmin ? 'cursor-pointer hover:text-cyber-green transition-colors' : ''
                  ]"
                  @dblclick="isAdmin && startEditCropType()"
                >{{ selectedCell.cropType || '双击设置' }}</span>
              </template>
            </div>
            <div v-if="selectedCell.pest" class="flex justify-between items-center py-2 border-b border-white/5">
              <span class="text-xs text-slate-400">活跃病虫害</span>
              <span class="text-sm text-amber">{{ selectedCell.pest }}</span>
            </div>
            <div class="flex justify-between items-center py-2">
              <span class="text-xs text-slate-400">风险评分</span>
              <span class="text-sm font-mono font-bold" :class="selectedCell.score >= 0.8 ? 'text-sakura' : selectedCell.score >= 0.5 ? 'text-amber' : 'text-cyber-green'">
                {{ (selectedCell.score * 100).toFixed(0) }}%
              </span>
            </div>
          </div>
          <div class="mt-4 pt-3 border-t border-white/5 text-[10px] text-slate-600 font-mono text-center">
            热力图颜色由该区域活跃工单的最高威胁等级决定
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

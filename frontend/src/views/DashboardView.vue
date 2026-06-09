<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import GlassCard from '../components/GlassCard.vue'
import DataMetric from '../components/DataMetric.vue'
import { useWorkOrderStore } from '../stores/workorder'
import { mockEnvironmentData, mockGrowthMetrics, mockCameras, mockGridHeatmap } from '../mock/data'

const env = mockEnvironmentData
const cameras = mockCameras
const woStore = useWorkOrderStore()

// Editable metadata
const meta = reactive({
  sectorId: 'GH-A1',
  cropSpecies: 'Solanum lycopersicum',
  plantingDate: '2026-03-15',
  location: '34.2614N, 108.9423E',
  area: '2400 m²',
})
const editingMeta = ref(false)

// 9个网格基础数据
const gridLabels = ['A1', 'A2', 'A3', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3']

// 根据工单动态计算每个网格的状态
const gridCells = computed(() => {
  return gridLabels.map(label => {
    const base = mockGridHeatmap.find(c => c.label === label)!
    const severity = woStore.gridSeverityMap[label]
    const activeOrder = woStore.orders.find(o => o.gridLabel === label && (o.status === 'PENDING' || o.status === 'PROCESSING'))
    return {
      label,
      severity: severity || null,
      pest: activeOrder?.pestName || '',
      score: base.score,
    }
  })
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

// 报警列表
const alerts = computed(() => woStore.alerts)

// Detection confidence threshold (0-1), will be sent to backend
const confidenceThreshold = ref(0.5)

// Heatmap cell detail
const selectedCell = ref<{ label: string; severity: string | null; pest: string; score: number } | null>(null)

function selectCell(cell: { label: string; severity: string | null; pest: string; score: number }) {
  selectedCell.value = cell
}

function closeCellDetail() {
  selectedCell.value = null
}

// Real-time clock
const now = ref(new Date())
let clockTimer: ReturnType<typeof setInterval>
onMounted(() => {
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
})
onUnmounted(() => {
  clearInterval(clockTimer)
})

// 2.5D Heatmap drag-to-rotate
const heatmapRotate = reactive({ x: 15, z: -2 })
const isDragging = ref(false)
const isAutoRotating = ref(true)
let dragStart = { x: 0, y: 0 }
let rotateStart = { x: 0, z: 0 }
let autoRotateTimer: ReturnType<typeof setInterval> | null = null
let pauseTimer: ReturnType<typeof setTimeout> | null = null

function startAutoRotation() {
  if (autoRotateTimer) return
  isAutoRotating.value = true
  const baseZ = heatmapRotate.z
  const startTime = Date.now()
  const cycleDuration = 30000 // 30 seconds per cycle

  autoRotateTimer = setInterval(() => {
    if (isDragging.value) return
    const elapsed = Date.now() - startTime
    const progress = (elapsed % cycleDuration) / cycleDuration
    heatmapRotate.z = baseZ + progress * 360
  }, 16) // ~60fps
}

function stopAutoRotation() {
  if (autoRotateTimer) {
    clearInterval(autoRotateTimer)
    autoRotateTimer = null
  }
  isAutoRotating.value = false
}

function pauseAutoRotation() {
  stopAutoRotation()
  if (pauseTimer) clearTimeout(pauseTimer)
  pauseTimer = setTimeout(() => {
    startAutoRotation()
  }, 5000) // Resume after 5 seconds of inactivity
}

function onDragStart(e: MouseEvent) {
  isDragging.value = true
  dragStart = { x: e.clientX, y: e.clientY }
  rotateStart = { x: heatmapRotate.x, z: heatmapRotate.z }
  stopAutoRotation()
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  const dx = e.clientX - dragStart.x
  const dy = e.clientY - dragStart.y
  heatmapRotate.z = Math.max(-60, Math.min(60, rotateStart.z + dx * 0.3))
  heatmapRotate.x = Math.max(-30, Math.min(60, rotateStart.x - dy * 0.3))
}

function onDragEnd() {
  isDragging.value = false
  pauseAutoRotation()
}

function resetRotation() {
  heatmapRotate.x = 15
  heatmapRotate.z = -2
  stopAutoRotation()
  startAutoRotation()
}

onMounted(() => {
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
  startAutoRotation()
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
  stopAutoRotation()
  if (pauseTimer) clearTimeout(pauseTimer)
})

// ECharts — trend chart (dynamic from store)
const trendChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const data = woStore.trendData
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

onMounted(() => {
  renderTrendChart()
})

// Watch for order changes and re-render chart
watch(() => woStore.orders.length, () => {
  renderTrendChart()
})
watch(() => woStore.orders.map(o => `${o.id}:${o.type}:${o.status}`).join(','), () => {
  renderTrendChart()
})
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

    <!-- 3-column layout -->
    <div class="flex-1 flex gap-4 min-h-0 overflow-hidden">
      <!-- LEFT PANEL -->
      <div class="w-72 flex flex-col gap-4 shrink-0 overflow-y-auto">
        <!-- Environmental Grid 2x2 -->
        <div class="grid grid-cols-2 gap-3">
          <DataMetric label="空气温度" :value="env.airTemp.value" unit="°C" :status="env.airTemp.status" />
          <DataMetric label="土壤湿度" :value="env.soilMoisture.value" unit="%" :status="env.soilMoisture.status" />
          <DataMetric label="空气湿度" :value="env.humidity.value" unit="%" :status="env.humidity.status" />
          <DataMetric label="光照强度" :value="env.lightLevel.value" unit="lux" :status="env.lightLevel.status" />
        </div>

        <!-- Confidence Threshold -->
        <GlassCard>
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
            <span class="text-[10px] font-mono text-sakura">{{ alerts.filter(a => a.level === 'critical').length }} 严重</span>
          </div>
          <div class="space-y-2 overflow-y-auto flex-1 min-h-0">
            <div
              v-for="alert in alerts"
              :key="alert.id"
              class="px-3 py-2 rounded-lg border transition-all"
              :class="alert.level === 'critical'
                ? 'bg-sakura/5 border-sakura/20 glow-red'
                : 'bg-amber/5 border-amber/20 glow-amber'"
            >
              <div class="text-xs text-white leading-relaxed">{{ alert.message }}</div>
              <div class="text-[10px] text-slate-600 font-mono mt-1">{{ alert.time }}</div>
            </div>
          </div>
        </GlassCard>
      </div>

      <!-- CENTER PANEL -->
      <div class="flex-1 flex flex-col gap-4 min-w-0 overflow-hidden">
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

        <!-- Metadata Matrix (editable) -->
        <div class="grid grid-cols-5 gap-3 shrink-0">
          <div class="glass rounded-lg px-3 py-2 text-center relative group">
            <div class="text-[10px] text-slate-500 mb-0.5">区域</div>
            <template v-if="!editingMeta">
              <div class="text-xs font-mono text-white">{{ meta.sectorId }}</div>
            </template>
            <template v-else>
              <input v-model="meta.sectorId" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" />
            </template>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">作物</div>
            <template v-if="!editingMeta">
              <div class="text-xs font-mono text-white truncate">{{ meta.cropSpecies.split(' ')[0] }}</div>
            </template>
            <template v-else>
              <input v-model="meta.cropSpecies" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" />
            </template>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">定植日期</div>
            <template v-if="!editingMeta">
              <div class="text-xs font-mono text-white">{{ meta.plantingDate }}</div>
            </template>
            <template v-else>
              <input v-model="meta.plantingDate" type="date" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" />
            </template>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">位置</div>
            <template v-if="!editingMeta">
              <div class="text-xs font-mono text-white">{{ meta.location.split(',')[0] }}</div>
            </template>
            <template v-else>
              <input v-model="meta.location" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" />
            </template>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">面积</div>
            <template v-if="!editingMeta">
              <div class="text-xs font-mono text-white">{{ meta.area }}</div>
            </template>
            <template v-else>
              <input v-model="meta.area" class="w-full text-xs font-mono text-white bg-transparent border-b border-cyber-green/50 text-center outline-none" />
            </template>
          </div>
        </div>
        <!-- Edit button -->
        <div class="flex justify-end shrink-0">
          <button
            class="px-3 py-1 rounded-lg text-[10px] font-mono transition-colors"
            :class="editingMeta ? 'bg-cyber-green/10 text-cyber-green border border-cyber-green/20' : 'bg-white/5 text-slate-500 border border-white/10 hover:bg-white/10'"
            @click="editingMeta = !editingMeta"
          >
            {{ editingMeta ? '保存' : '编辑信息' }}
          </button>
        </div>
      </div>

      <!-- RIGHT PANEL -->
      <div class="w-[34rem] flex flex-col gap-4 shrink-0 overflow-y-auto">
        <!-- Growth Metrics -->
        <GlassCard>
          <div class="text-xs text-slate-400 tracking-wider mb-3">生长指标</div>
          <div class="space-y-2.5">
            <div v-for="m in mockGrowthMetrics" :key="m.label" class="flex items-center gap-3">
              <span class="text-[10px] text-slate-500 w-14 shrink-0">{{ m.label }}</span>
              <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-1000"
                  :style="{ width: `${Math.min((m.value / (m.label.includes('CO') ? 600 : m.label.includes('pH') ? 14 : m.label === 'EC' ? 3 : m.label.includes('温度') ? 40 : 300)) * 100, 100)}%`, backgroundColor: m.color }"
                />
              </div>
              <span class="text-xs font-mono text-white w-20 text-right shrink-0">{{ m.value }} {{ m.unit }}</span>
            </div>
          </div>
        </GlassCard>

        <!-- Trend Chart -->
        <GlassCard class="shrink-0">
          <div class="text-xs text-slate-400 tracking-wider mb-3">7日趋势</div>
          <div ref="trendChartRef" class="h-48" />
        </GlassCard>

        <!-- Camera Monitoring -->
        <GlassCard class="flex-1 min-h-0 flex flex-col">
          <div class="flex items-center justify-between mb-3 shrink-0">
            <span class="text-xs text-slate-400 tracking-wider">实时监控</span>
            <span class="text-[10px] font-mono text-cyber-green">{{ cameras.filter(c => c.status === 'ONLINE').length }}/{{ cameras.length }} 在线</span>
          </div>
          <div class="grid grid-cols-3 gap-2 flex-1 min-h-0">
            <div
              v-for="camera in cameras"
              :key="camera.id"
              class="glass rounded-lg overflow-hidden flex flex-col"
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
                  <span class="text-[9px] text-slate-500 font-mono">{{ camera.grid }}</span>
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
        @click.self="closeCellDetail"
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
            <button class="w-7 h-7 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-white transition-colors" @click="closeCellDetail">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l6 6M6 12L6 6l6 6"/></svg>
            </button>
          </div>
          <div class="space-y-3">
            <div class="flex justify-between items-center py-2 border-b border-white/5">
              <span class="text-xs text-slate-400">威胁等级</span>
              <span class="text-sm font-mono font-bold" :class="getCellLabelColor(selectedCell.severity)">
                {{ getCellLabel(selectedCell.severity) }}
              </span>
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

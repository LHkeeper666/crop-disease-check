<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import GlassCard from '../components/GlassCard.vue'
import DataMetric from '../components/DataMetric.vue'
import { mockEnvironmentData, mockAlerts, mockGrowthMetrics, mockPowerStream, mockGreenhouseMeta, mockGridHeatmap } from '../mock/data'

const env = mockEnvironmentData
const alerts = mockAlerts
const meta = mockGreenhouseMeta

// Detection confidence threshold (0-1), will be sent to backend
const confidenceThreshold = ref(0.5)

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
let dragStart = { x: 0, y: 0 }
let rotateStart = { x: 0, z: 0 }

function onDragStart(e: MouseEvent) {
  isDragging.value = true
  dragStart = { x: e.clientX, y: e.clientY }
  rotateStart = { x: heatmapRotate.x, z: heatmapRotate.z }
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
}

function resetRotation() {
  heatmapRotate.x = 15
  heatmapRotate.z = -2
}

onMounted(() => {
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
})

// ECharts refs
const trendChartRef = ref<HTMLDivElement>()
const powerChartRef = ref<HTMLDivElement>()

onMounted(() => {
  // Trend chart
  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    chart.setOption({
      backgroundColor: 'transparent',
      grid: { top: 20, right: 15, bottom: 25, left: 40 },
      xAxis: {
        type: 'category',
        data: ['06-03', '06-04', '06-05', '06-06', '06-07', '06-08', '06-09'],
        axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
        axisLabel: { color: 'rgba(255,255,255,0.4)', fontSize: 10, fontFamily: 'JetBrains Mono' },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
        axisLabel: { color: 'rgba(255,255,255,0.3)', fontSize: 10, fontFamily: 'JetBrains Mono' },
      },
      series: [
        {
          name: '病害',
          type: 'bar',
          stack: 'total',
          data: [10, 14, 8, 18, 20, 16, 7],
          itemStyle: { color: '#EF4444', borderRadius: [0, 0, 0, 0] },
          barWidth: 16,
        },
        {
          name: '虫害',
          type: 'bar',
          stack: 'total',
          data: [8, 8, 7, 10, 12, 9, 5],
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

  // Power stream chart
  if (powerChartRef.value) {
    const chart = echarts.init(powerChartRef.value)
    const data = mockPowerStream
    chart.setOption({
      backgroundColor: 'transparent',
      grid: { top: 10, right: 10, bottom: 10, left: 10 },
      xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
      yAxis: { type: 'value', show: false },
      series: [{
        type: 'line',
        data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: '#4ADE80' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(74,222,128,0.3)' },
            { offset: 1, color: 'rgba(74,222,128,0)' },
          ]),
        },
      }],
    })
  }
})

function getHeatColor(score: number) {
  if (score >= 0.8) return 'bg-sakura/60'
  if (score >= 0.5) return 'bg-amber/50'
  if (score >= 0.3) return 'bg-yellow-500/30'
  return 'bg-cyber-green/20'
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">2.5D 植物生理遥测舱</h1>
        <p class="text-xs text-slate-500 font-mono">REAL-TIME AGRI-PERCEPTION CORE</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-cyber-green/10 border border-cyber-green/20">
          <span class="w-2 h-2 rounded-full bg-cyber-green pulse-green" />
          <span class="text-xs text-cyber-green font-mono">ONLINE</span>
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
          <DataMetric label="Air Temp" :value="env.airTemp.value" unit="C" :status="env.airTemp.status" />
          <DataMetric label="Soil Moist" :value="env.soilMoisture.value" unit="%" :status="env.soilMoisture.status" />
          <DataMetric label="Humidity" :value="env.humidity.value" unit="%" :status="env.humidity.status" />
          <DataMetric label="Light" :value="env.lightLevel.value" unit="lux" :status="env.lightLevel.status" />
        </div>

        <!-- Confidence Threshold -->
        <GlassCard>
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs text-slate-400 uppercase tracking-wider">Detection Threshold</span>
            <span class="text-sm font-mono font-bold" :class="confidenceThreshold >= 0.7 ? 'text-cyber-green' : confidenceThreshold >= 0.4 ? 'text-amber' : 'text-sakura'">
              {{ (confidenceThreshold * 100).toFixed(0) }}%
            </span>
          </div>
          <!-- Custom range slider -->
          <div class="slider-wrapper relative h-7 flex items-center">
            <!-- Background track -->
            <div class="absolute inset-x-0 top-1/2 -translate-y-1/2 h-2 rounded-full bg-white/5 pointer-events-none" />
            <!-- Active fill — exactly under the thumb -->
            <div
              class="absolute left-0 top-1/2 -translate-y-1/2 h-2 rounded-full pointer-events-none"
              :class="confidenceThreshold >= 0.7 ? 'bg-cyber-green/60' : confidenceThreshold >= 0.4 ? 'bg-amber/60' : 'bg-sakura/60'"
              :style="{ width: `${confidenceThreshold * 100}%` }"
            />
            <!-- Native range input (transparent track, only thumb visible) -->
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
        <GlassCard class="flex-1 min-h-0">
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs text-slate-400 uppercase tracking-wider">Alerts</span>
            <span class="text-[10px] font-mono text-sakura">{{ alerts.filter(a => a.level === 'critical').length }} critical</span>
          </div>
          <div class="space-y-2 overflow-y-auto max-h-48">
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
            <span class="text-xs text-slate-400 uppercase tracking-wider">2.5D Spatial Heatmap</span>
            <div class="flex gap-2">
              <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-cyber-green/10 text-cyber-green border border-cyber-green/20">Disease</button>
              <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-white/5 text-slate-500 border border-white/10 hover:bg-white/10">Pest</button>
              <button
                class="px-3 py-1 rounded-lg text-[10px] font-mono bg-white/5 text-slate-500 border border-white/10 hover:bg-white/10"
                @click="resetRotation"
              >Reset</button>
            </div>
          </div>
          <!-- Grid -->
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
                v-for="cell in mockGridHeatmap"
                :key="cell.label"
                class="aspect-square rounded-xl border border-white/10 flex flex-col items-center justify-center cursor-pointer transition-all duration-300 hover:scale-105 hover:border-white/25"
                :class="getHeatColor(cell.score)"
              >
                <span class="text-sm font-mono font-bold text-white">{{ cell.label }}</span>
                <span class="text-[10px] font-mono text-slate-300 mt-0.5">{{ (cell.score * 100).toFixed(0) }}%</span>
                <span v-if="cell.pest" class="text-[9px] text-slate-400 mt-0.5">{{ cell.pest }}</span>
              </div>
            </div>
          </div>
          <!-- Legend -->
          <div class="flex items-center justify-center gap-4 mt-3">
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-cyber-green/20" />
              <span class="text-[10px] text-slate-500">Safe</span>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-amber/50" />
              <span class="text-[10px] text-slate-500">Warning</span>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="w-3 h-3 rounded bg-sakura/60" />
              <span class="text-[10px] text-slate-500">Critical</span>
            </div>
          </div>
        </GlassCard>

        <!-- Metadata Matrix -->
        <div class="grid grid-cols-5 gap-3 shrink-0">
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">Sector</div>
            <div class="text-xs font-mono text-white">{{ meta.sectorId }}</div>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">Crop</div>
            <div class="text-xs font-mono text-white truncate">{{ meta.cropSpecies.split(' ')[0] }}</div>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">Planted</div>
            <div class="text-xs font-mono text-white">{{ meta.plantingDate }}</div>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">Location</div>
            <div class="text-xs font-mono text-white">{{ meta.location.split(',')[0] }}</div>
          </div>
          <div class="glass rounded-lg px-3 py-2 text-center">
            <div class="text-[10px] text-slate-500 mb-0.5">Area</div>
            <div class="text-xs font-mono text-white">{{ meta.area }}</div>
          </div>
        </div>
      </div>

      <!-- RIGHT PANEL -->
      <div class="w-72 flex flex-col gap-4 shrink-0 overflow-y-auto">
        <!-- Growth Metrics -->
        <GlassCard>
          <div class="text-xs text-slate-400 uppercase tracking-wider mb-3">Growth Indices</div>
          <div class="space-y-2.5">
            <div v-for="m in mockGrowthMetrics" :key="m.label" class="flex items-center gap-3">
              <span class="text-[10px] text-slate-500 w-12 font-mono">{{ m.label }}</span>
              <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-1000"
                  :style="{ width: `${Math.min((m.value / (m.label === 'CO2' ? 600 : m.label === 'Soil pH' ? 14 : m.label === 'EC' ? 3 : m.label === 'Temp' ? 40 : 300)) * 100, 100)}%`, backgroundColor: m.color }"
                />
              </div>
              <span class="text-xs font-mono text-white w-14 text-right">{{ m.value }} {{ m.unit }}</span>
            </div>
          </div>
        </GlassCard>

        <!-- Trend Chart -->
        <GlassCard class="flex-1 min-h-0 flex flex-col">
          <div class="text-xs text-slate-400 uppercase tracking-wider mb-3">7-Day Trend</div>
          <div ref="trendChartRef" class="flex-1 min-h-[180px]" />
        </GlassCard>

        <!-- Power Stream -->
        <GlassCard>
          <div class="text-xs text-slate-400 uppercase tracking-wider mb-3">Power Stream</div>
          <div ref="powerChartRef" class="h-24" />
          <div class="flex justify-between mt-1 text-[10px] text-slate-600 font-mono">
            <span>-60s</span>
            <span>now</span>
          </div>
        </GlassCard>
      </div>
    </div>
  </div>
</template>

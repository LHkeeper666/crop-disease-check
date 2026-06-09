<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import GlassCard from '../components/GlassCard.vue'
import GlowButton from '../components/GlowButton.vue'
import { mockStatsOverview, mockDailyReports } from '../mock/data'

const stats = mockStatsOverview
const reports = ref(mockDailyReports)

const pieChartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()

onMounted(() => {
  // Pie chart - pest type distribution
  if (pieChartRef.value) {
    const chart = echarts.init(pieChartRef.value)
    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15,23,42,0.9)',
        borderColor: 'rgba(255,255,255,0.1)',
        textStyle: { color: '#e2e8f0', fontSize: 12 },
      },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#0B0F19', borderWidth: 2 },
        label: { show: true, color: 'rgba(255,255,255,0.6)', fontSize: 10, fontFamily: 'JetBrains Mono' },
        emphasis: {
          label: { show: true, fontSize: 12, fontWeight: 'bold' },
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' },
        },
        data: stats.typeDistribution.map((d, i) => ({
          ...d,
          itemStyle: { color: ['#EF4444', '#FFB300', '#4ADE80', '#3B82F6', '#8B5CF6', '#6B7280'][i] },
        })),
      }],
    })
  }

  // Trend chart
  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    chart.setOption({
      backgroundColor: 'transparent',
      grid: { top: 30, right: 20, bottom: 25, left: 45 },
      legend: {
        data: ['病害', '虫害'],
        textStyle: { color: 'rgba(255,255,255,0.5)', fontSize: 10 },
        top: 0,
        right: 0,
      },
      xAxis: {
        type: 'category',
        data: stats.dailyTrend.map(d => d.date),
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
          type: 'line',
          data: stats.dailyTrend.map(d => Math.round(d.count * 0.6)),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { color: '#EF4444', width: 2 },
          itemStyle: { color: '#EF4444' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(239,68,68,0.2)' },
              { offset: 1, color: 'rgba(239,68,68,0)' },
            ]),
          },
        },
        {
          name: '虫害',
          type: 'line',
          data: stats.dailyTrend.map(d => Math.round(d.count * 0.4)),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { color: '#FFB300', width: 2 },
          itemStyle: { color: '#FFB300' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(255,179,0,0.2)' },
              { offset: 1, color: 'rgba(255,179,0,0)' },
            ]),
          },
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
})
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">离线农情复盘与高级分析舱</h1>
        <p class="text-xs text-slate-500 font-mono">离线农情复盘与高级分析</p>
      </div>
      <div class="flex gap-2">
        <GlowButton label="导出 Excel" variant="secondary" />
        <GlowButton label="生成日报" />
      </div>
    </div>

    <!-- Stats cards -->
    <div class="grid grid-cols-5 gap-3 shrink-0">
      <div class="glass rounded-xl px-4 py-3 text-center">
        <div class="text-2xl font-mono font-bold text-white">{{ stats.totalReports }}</div>
        <div class="text-[10px] text-slate-500 mt-1">总上报数</div>
      </div>
      <div class="glass rounded-xl px-4 py-3 text-center">
        <div class="text-2xl font-mono font-bold text-cyber-green">{{ stats.todayReports }}</div>
        <div class="text-[10px] text-slate-500 mt-1">今日上报</div>
      </div>
      <div class="glass rounded-xl px-4 py-3 text-center">
        <div class="text-2xl font-mono font-bold text-amber">{{ stats.pendingAudit }}</div>
        <div class="text-[10px] text-slate-500 mt-1">待审核</div>
      </div>
      <div class="glass rounded-xl px-4 py-3 text-center">
        <div class="text-2xl font-mono font-bold text-cyber-green-dark">{{ stats.processed }}</div>
        <div class="text-[10px] text-slate-500 mt-1">已处理</div>
      </div>
      <div class="glass rounded-xl px-4 py-3 text-center">
        <div class="text-2xl font-mono font-bold text-sakura">{{ stats.highRiskAlerts }}</div>
        <div class="text-[10px] text-slate-500 mt-1">高风险预警</div>
      </div>
    </div>

    <!-- Charts row -->
    <div class="flex gap-4 flex-1 min-h-0 overflow-hidden">
      <!-- Pie chart -->
      <GlassCard class="w-80 shrink-0 flex flex-col">
        <div class="text-xs text-slate-400 tracking-wider mb-3">虫害分布</div>
        <div ref="pieChartRef" class="flex-1 min-h-[200px]" />
      </GlassCard>

      <!-- Trend chart -->
      <GlassCard class="flex-1 min-w-0 flex flex-col">
        <div class="flex items-center justify-between mb-3">
          <span class="text-xs text-slate-400 tracking-wider">7日趋势</span>
          <div class="flex gap-2">
            <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-cyber-green/10 text-cyber-green border border-cyber-green/20">7天</button>
            <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-white/5 text-slate-500 border border-white/10 hover:bg-white/10">30天</button>
          </div>
        </div>
        <div ref="trendChartRef" class="flex-1 min-h-[200px]" />
      </GlassCard>
    </div>

    <!-- Top 5 and daily reports -->
    <div class="flex gap-4 shrink-0 overflow-hidden">
      <!-- Top 5 pests -->
      <GlassCard class="w-80 shrink-0">
        <div class="text-xs text-slate-400 tracking-wider mb-3">Top 5 虫害</div>
        <div class="space-y-2.5">
          <div v-for="(pest, idx) in stats.top5Pests" :key="pest.name" class="flex items-center gap-3">
            <span class="text-xs font-mono text-slate-600 w-4">{{ idx + 1 }}</span>
            <span class="text-xs text-white flex-1">{{ pest.name }}</span>
            <div class="w-24 h-2 rounded-full bg-white/5 overflow-hidden">
              <div
                class="h-full rounded-full bg-gradient-to-r from-sunset-from to-sunset-to"
                :style="{ width: `${(pest.count / stats.top5Pests[0].count) * 100}%` }"
              />
            </div>
            <span class="text-xs font-mono text-slate-400 w-8 text-right">{{ pest.count }}</span>
          </div>
        </div>
      </GlassCard>

      <!-- Daily reports table -->
      <GlassCard class="flex-1 min-w-0 overflow-hidden flex flex-col">
        <div class="text-xs text-slate-400 tracking-wider mb-3">每日报告</div>
        <div class="flex-1 overflow-y-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-[10px] text-slate-500 uppercase tracking-wider border-b border-white/5">
                <th class="pb-2 pr-3">日期</th>
                <th class="pb-2 pr-3">巡检</th>
                <th class="pb-2 pr-3">识别</th>
                <th class="pb-2 pr-3">病害</th>
                <th class="pb-2 pr-3">虫害</th>
                <th class="pb-2 pr-3">处理率</th>
                <th class="pb-2">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rpt in reports" :key="rpt.id" class="border-b border-white/5 hover:bg-white/5 transition-colors">
                <td class="py-2 pr-3 font-mono text-xs text-white">{{ rpt.date }}</td>
                <td class="py-2 pr-3 font-mono text-xs text-slate-400">{{ rpt.inspections }}</td>
                <td class="py-2 pr-3 font-mono text-xs text-slate-400">{{ rpt.detections }}</td>
                <td class="py-2 pr-3 font-mono text-xs text-sakura">{{ rpt.disease }}</td>
                <td class="py-2 pr-3 font-mono text-xs text-amber">{{ rpt.pest }}</td>
                <td class="py-2 pr-3 font-mono text-xs text-cyber-green">{{ (rpt.handledRate * 100).toFixed(0) }}%</td>
                <td class="py-2">
                  <span class="px-2 py-0.5 rounded text-[10px] font-mono" :class="rpt.emailSent ? 'text-cyber-green bg-cyber-green/10' : 'text-slate-500 bg-white/5'">
                    {{ rpt.emailSent ? '已发送' : '待发送' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </GlassCard>
    </div>
  </div>
</template>

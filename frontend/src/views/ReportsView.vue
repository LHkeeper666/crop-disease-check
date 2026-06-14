<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType,
} from 'docx'
import { saveAs } from 'file-saver'
import { useRouter } from 'vue-router'
import GlassCard from '../components/GlassCard.vue'
import GlowButton from '../components/GlowButton.vue'
import { fetchStatisticsOverview, type StatisticsOverviewVO } from '../api/statistics'
import { fetchDailyReports, generateDailyReport, type DailyReportVO } from '../api/dailyReport'

const router = useRouter()

const loading = ref(true)
const overview = ref<StatisticsOverviewVO | null>(null)
const reports = ref<DailyReportVO[]>([])
const todayGenerated = ref(false)
const apiAvailable = ref(false) // Agent API Key 是否已配置

const today = new Date().toISOString().slice(0, 10)

async function checkApiKey() {
  try {
    const token = localStorage.getItem('treeforge_token') || ''
    const res = await fetch('/api/agri-brain/config', {
      headers: { 'Authorization': `Bearer ${token}` },
    })
    const data = await res.json()
    apiAvailable.value = !!(data?.data?.hasApiKey)
  } catch {
    apiAvailable.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const [overviewData, reportPage] = await Promise.all([
      fetchStatisticsOverview(),
      fetchDailyReports({ size: 30 }),
    ])
    overview.value = overviewData
    reports.value = reportPage.records
    // 检查今日是否已生成
    todayGenerated.value = reportPage.records.some(r => r.reportDate === today)
  } catch (e: any) {
    console.error('[ReportsView] 加载数据失败:', e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
  checkApiKey()
})

async function handleGenerateDailyReport() {
  if (todayGenerated.value) return
  try {
    await generateDailyReport(today)
    todayGenerated.value = true
    // 仅刷新报告列表，不重新加载 overview 以避免数据大屏内容消失
    try {
      const reportPage = await fetchDailyReports({ size: 30 })
      reports.value = reportPage.records
    } catch {
      // 报告列表刷新失败不影响主流程
    }
    // 生成 Word 文档并下载
    await exportDailyReportWord()
  } catch (e: any) {
    console.error('[ReportsView] 生成日报失败:', e.message)
    alert('生成日报失败: ' + e.message)
  }
}

// 统计卡片数据（从 overview 映射）
const statsCards = ref([
  { key: 'totalReports', label: '总上报数', value: 0, color: 'text-white' },
  { key: 'todayReports', label: '今日上报', value: 0, color: 'text-cyber-green' },
  { key: 'pendingAudit', label: '待审核', value: 0, color: 'text-amber' },
  { key: 'processed', label: '已处理', value: 0, color: 'text-cyber-green-dark' },
  { key: 'highRiskAlerts', label: '高风险预警', value: 0, color: 'text-sakura' },
])

watch(overview, (ov) => {
  if (!ov) return
  statsCards.value = statsCards.value.map(c => ({
    ...c,
    value: (ov as any)[c.key] ?? 0,
  }))
})

const diseaseChartRef = ref<HTMLDivElement>()
const pestChartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()

let diseaseChart: echarts.ECharts | null = null
let pestChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

function handleResize() {
  diseaseChart?.resize()
  pestChart?.resize()
  trendChart?.resize()
}

function exportToExcel() {
  const headers = ['日期', '识别数', '病害数', '虫害数', '处理率', '状态']
  const rows = reports.value.map(rpt => [
    rpt.reportDate,
    rpt.summary?.totalDetections ?? 0,
    rpt.summary?.diseaseCount ?? 0,
    rpt.summary?.pestCount ?? 0,
    `${((rpt.summary?.workorderHandledRate ?? 0) * 100).toFixed(0)}%`,
    rpt.reportDate === today && todayGenerated.value ? '已生成' : rpt.reportDate === today ? '未生成' : '已生成',
  ])
  const sheetData = [headers, ...rows]
  const ws = XLSX.utils.aoa_to_sheet(sheetData)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '每日报告')
  XLSX.writeFile(wb, `农情报表_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.xlsx`)
}

async function exportDailyReportWord() {
  const ov = overview.value
  const headerBorder = { style: BorderStyle.SINGLE, size: 1, color: 'CCCCCC' }
  const cellBorder = { top: headerBorder, bottom: headerBorder, left: headerBorder, right: headerBorder }

  // Stats summary table
  const statsRows = statsCards.value.map(card =>
    new TableRow({
      children: [
        new TableCell({
          width: { size: 40, type: WidthType.PERCENTAGE },
          borders: cellBorder,
          children: [new Paragraph({ children: [new TextRun({ text: card.label, font: 'Microsoft YaHei' })] })],
        }),
        new TableCell({
          width: { size: 60, type: WidthType.PERCENTAGE },
          borders: cellBorder,
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            children: [new TextRun({ text: String(card.value), bold: true, font: 'JetBrains Mono' })],
          })],
        }),
      ],
    })
  )

  // Daily reports table
  const reportHeaderRow = new TableRow({
    children: ['日期', '识别数', '病害', '虫害', '处理率'].map(h =>
      new TableCell({
        borders: cellBorder,
        shading: { type: ShadingType.SOLID, color: '1E293B' },
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: h, bold: true, color: 'FFFFFF', font: 'Microsoft YaHei', size: 20 })],
        })],
      })
    ),
  })

  const reportRows = reports.value.map(rpt =>
    new TableRow({
      children: [
        rpt.reportDate,
        String(rpt.summary?.totalDetections ?? 0),
        String(rpt.summary?.diseaseCount ?? 0),
        String(rpt.summary?.pestCount ?? 0),
        `${((rpt.summary?.workorderHandledRate ?? 0) * 100).toFixed(0)}%`,
      ].map(val =>
        new TableCell({
          borders: cellBorder,
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [new TextRun({ text: val, font: 'JetBrains Mono', size: 20 })],
          })],
        })
      ),
    })
  )

  // Disease distribution
  const diseaseData = ov?.diseaseDistribution || []
  const diseaseRows = diseaseData.map(d =>
    new TableRow({
      children: [
        new TableCell({
          borders: cellBorder,
          children: [new Paragraph({ children: [new TextRun({ text: d.name, font: 'Microsoft YaHei', size: 20 })] })],
        }),
        new TableCell({
          borders: cellBorder,
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            children: [new TextRun({ text: String(d.value), font: 'JetBrains Mono', size: 20 })],
          })],
        }),
      ],
    })
  )

  // Pest distribution
  const pestData = ov?.pestDistribution || []
  const pestRows = pestData.map(d =>
    new TableRow({
      children: [
        new TableCell({
          borders: cellBorder,
          children: [new Paragraph({ children: [new TextRun({ text: d.name, font: 'Microsoft YaHei', size: 20 })] })],
        }),
        new TableCell({
          borders: cellBorder,
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            children: [new TextRun({ text: String(d.value), font: 'JetBrains Mono', size: 20 })],
          })],
        }),
      ],
    })
  )

  const doc = new Document({
    sections: [{
      children: [
        new Paragraph({
          heading: HeadingLevel.HEADING_1,
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: '农情报表日报', bold: true, font: 'Microsoft YaHei', size: 36 })],
        }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { after: 400 },
          children: [new TextRun({ text: `生成日期: ${today}    农作物疾病检测系统`, font: 'Microsoft YaHei', size: 20, color: '666666' })],
        }),

        // Section 1: Overview stats
        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text: '一、数据概览', font: 'Microsoft YaHei' })] }),
        new Table({
          width: { size: 100, type: WidthType.PERCENTAGE },
          rows: [
            new TableRow({
              children: [
                new TableCell({
                  width: { size: 40, type: WidthType.PERCENTAGE },
                  borders: cellBorder,
                  shading: { type: ShadingType.SOLID, color: '1E293B' },
                  children: [new Paragraph({ children: [new TextRun({ text: '指标', bold: true, color: 'FFFFFF', font: 'Microsoft YaHei' })] })],
                }),
                new TableCell({
                  width: { size: 60, type: WidthType.PERCENTAGE },
                  borders: cellBorder,
                  shading: { type: ShadingType.SOLID, color: '1E293B' },
                  children: [new Paragraph({
                    alignment: AlignmentType.RIGHT,
                    children: [new TextRun({ text: '数值', bold: true, color: 'FFFFFF', font: 'Microsoft YaHei' })],
                  })],
                }),
              ],
            }),
            ...statsRows,
          ],
        }),

        // Section 2: Disease distribution
        new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 400 }, children: [new TextRun({ text: '二、病害分布', font: 'Microsoft YaHei' })] }),
        diseaseRows.length > 0
          ? new Table({
              width: { size: 100, type: WidthType.PERCENTAGE },
              rows: [
                new TableRow({
                  children: [
                    new TableCell({
                      borders: cellBorder,
                      shading: { type: ShadingType.SOLID, color: '1E293B' },
                      children: [new Paragraph({ children: [new TextRun({ text: '病害名称', bold: true, color: 'FFFFFF', font: 'Microsoft YaHei', size: 20 })] })],
                    }),
                    new TableCell({
                      borders: cellBorder,
                      shading: { type: ShadingType.SOLID, color: '1E293B' },
                      children: [new Paragraph({
                        alignment: AlignmentType.RIGHT,
                        children: [new TextRun({ text: '检出次数', bold: true, color: 'FFFFFF', font: 'Microsoft YaHei', size: 20 })],
                      })],
                    }),
                  ],
                }),
                ...diseaseRows,
              ],
            })
          : new Paragraph({ children: [new TextRun({ text: '暂无病害数据', font: 'Microsoft YaHei', color: '999999' })] }),

        // Section 3: Pest distribution
        new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 400 }, children: [new TextRun({ text: '三、虫害分布', font: 'Microsoft YaHei' })] }),
        pestRows.length > 0
          ? new Table({
              width: { size: 100, type: WidthType.PERCENTAGE },
              rows: [
                new TableRow({
                  children: [
                    new TableCell({
                      borders: cellBorder,
                      shading: { type: ShadingType.SOLID, color: '1E293B' },
                      children: [new Paragraph({ children: [new TextRun({ text: '虫害名称', bold: true, color: 'FFFFFF', font: 'Microsoft YaHei', size: 20 })] })],
                    }),
                    new TableCell({
                      borders: cellBorder,
                      shading: { type: ShadingType.SOLID, color: '1E293B' },
                      children: [new Paragraph({
                        alignment: AlignmentType.RIGHT,
                        children: [new TextRun({ text: '检出次数', bold: true, color: 'FFFFFF', font: 'Microsoft YaHei', size: 20 })],
                      })],
                    }),
                  ],
                }),
                ...pestRows,
              ],
            })
          : new Paragraph({ children: [new TextRun({ text: '暂无虫害数据', font: 'Microsoft YaHei', color: '999999' })] }),

        // Section 4: Daily reports
        new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 400 }, children: [new TextRun({ text: '四、每日报告明细', font: 'Microsoft YaHei' })] }),
        reports.value.length > 0
          ? new Table({
              width: { size: 100, type: WidthType.PERCENTAGE },
              rows: [reportHeaderRow, ...reportRows],
            })
          : new Paragraph({ children: [new TextRun({ text: '暂无报告数据', font: 'Microsoft YaHei', color: '999999' })] }),

        // Footer
        new Paragraph({
          spacing: { before: 600 },
          alignment: AlignmentType.CENTER,
          children: [new TextRun({
            text: '本报告由农作物疾病检测系统自动生成',
            font: 'Microsoft YaHei', size: 18, color: '999999', italics: true,
          })],
        }),
      ],
    }],
  })

  const blob = await Packer.toBlob(doc)
  saveAs(blob, `农情报表_${today}.docx`)
}

function renderCharts() {
  if (!overview.value) return

  // 病害分布饼图
  if (diseaseChartRef.value) {
    if (!diseaseChart) diseaseChart = echarts.init(diseaseChartRef.value)
    diseaseChart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15,23,42,0.9)',
        borderColor: 'rgba(255,255,255,0.1)',
        textStyle: { color: '#e2e8f0', fontSize: 12 },
      },
      series: [{
        type: 'pie',
        radius: ['29%', '50%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#0B0F19', borderWidth: 2 },
        label: {
          show: true,
          position: 'outside',
          color: 'rgba(255,255,255,0.8)',
          fontSize: 11,
          fontFamily: 'JetBrains Mono',
          formatter: '{b}\n{d}%',
        },
        labelLine: { show: true, lineStyle: { color: 'rgba(255,255,255,0.3)' }, smooth: true },
        emphasis: {
          label: { fontSize: 13, fontWeight: 'bold' },
          itemStyle: { shadowBlur: 20, shadowColor: 'rgba(239,68,68,0.3)' },
        },
        data: (overview.value.diseaseDistribution || []).map((d, i) => ({
          name: d.name,
          value: d.value,
          itemStyle: { color: ['#EF4444', '#F97316', '#F59E0B', '#6366F1', '#8B5CF6'][i % 5] },
        })),
      }],
    })
  }

  // 虫害分布饼图
  if (pestChartRef.value) {
    if (!pestChart) pestChart = echarts.init(pestChartRef.value)
    pestChart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15,23,42,0.9)',
        borderColor: 'rgba(255,255,255,0.1)',
        textStyle: { color: '#e2e8f0', fontSize: 12 },
      },
      series: [{
        type: 'pie',
        radius: ['29%', '50%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#0B0F19', borderWidth: 2 },
        label: {
          show: true,
          position: 'outside',
          color: 'rgba(255,255,255,0.8)',
          fontSize: 11,
          fontFamily: 'JetBrains Mono',
          formatter: '{b}\n{d}%',
        },
        labelLine: { show: true, lineStyle: { color: 'rgba(255,255,255,0.3)' }, smooth: true },
        emphasis: {
          label: { fontSize: 13, fontWeight: 'bold' },
          itemStyle: { shadowBlur: 20, shadowColor: 'rgba(255,179,0,0.3)' },
        },
        data: (overview.value.pestDistribution || []).map((d, i) => ({
          name: d.name,
          value: d.value,
          itemStyle: { color: ['#FFB300', '#4ADE80', '#3B82F6', '#8B5CF6', '#EC4899'][i % 5] },
        })),
      }],
    })
  }

  // 7日趋势折线图
  if (trendChartRef.value) {
    if (!trendChart) trendChart = echarts.init(trendChartRef.value)
    const trend = overview.value.dailyTrend || []
    trendChart.setOption({
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
        data: trend.map(d => d.date),
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
          data: trend.map(d => d.diseaseCount),
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
          data: trend.map(d => d.pestCount),
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
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  diseaseChart?.dispose()
  pestChart?.dispose()
  trendChart?.dispose()
})

// 数据加载完成后渲染图表
watch(overview, () => {
  setTimeout(renderCharts, 100)
}, { flush: 'post' })
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
        <GlowButton label="导出 Excel" @click="exportToExcel" />
        <GlowButton
          :label="!apiAvailable ? '请先配置 API Key' : todayGenerated ? '今日已生成' : '生成日报 (Word)'"
          :disabled="todayGenerated || !apiAvailable"
          :title="!apiAvailable ? '请先在智慧大脑页面配置 Agent API Key，接入后方可生成日报' : todayGenerated ? '今日日报已生成' : '生成今日日报（Agent 分析 + Word 下载）'"
          @click="handleGenerateDailyReport"
        />
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-slate-500 text-sm font-mono">加载中...</div>
    </div>

    <template v-else>
      <!-- Stats cards -->
      <div class="grid grid-cols-3 xl:grid-cols-5 gap-3 shrink-0">
        <div v-for="card in statsCards" :key="card.key" class="glass rounded-xl px-4 py-3 text-center">
          <div class="text-2xl font-mono font-bold" :class="card.color">{{ card.value }}</div>
          <div class="text-[10px] text-slate-500 mt-1">{{ card.label }}</div>
        </div>
      </div>

      <!-- Main content: left disease+pest modules, right trend — fixed ratio 35% | 65% -->
      <div class="flex-1 min-h-0 overflow-hidden" style="display: grid; grid-template-columns: 35fr 65fr; gap: 1rem;">
        <!-- Left: Disease + Pest modules -->
        <div class="flex flex-col gap-4 min-w-0">
          <!-- Disease module -->
          <GlassCard class="flex-1 min-h-0 flex flex-col">
            <div class="text-xs text-slate-400 tracking-wider mb-2 shrink-0">病害分布</div>
            <div ref="diseaseChartRef" class="flex-1 min-h-0" />
          </GlassCard>

          <!-- Pest module -->
          <GlassCard class="flex-1 min-h-0 flex flex-col">
            <div class="text-xs text-slate-400 tracking-wider mb-2 shrink-0">虫害分布</div>
            <div ref="pestChartRef" class="flex-1 min-h-0" />
          </GlassCard>
        </div>

        <!-- Right: Trend + Daily reports -->
        <div class="flex flex-col gap-4 min-w-0">
          <!-- Trend chart -->
          <GlassCard class="flex-1 min-h-0 flex flex-col">
            <div class="flex items-center justify-between mb-3 shrink-0">
              <span class="text-xs text-slate-400 tracking-wider">7日趋势</span>
              <div class="flex gap-2">
                <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-cyber-green/10 text-cyber-green border border-cyber-green/20">7天</button>
                <button class="px-3 py-1 rounded-lg text-[10px] font-mono bg-white/5 text-slate-500 border border-white/10 hover:bg-white/10">30天</button>
              </div>
            </div>
            <div ref="trendChartRef" class="flex-1 min-h-0" />
          </GlassCard>

          <!-- Daily reports table -->
          <GlassCard class="flex-1 min-h-0 overflow-hidden flex flex-col">
            <div class="text-xs text-slate-400 tracking-wider mb-3 shrink-0">每日报告</div>
            <div class="flex-1 overflow-y-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-left text-[10px] text-slate-500 uppercase tracking-wider border-b border-white/5">
                    <th class="pb-2 pr-3">日期</th>
                    <th class="pb-2 pr-3">识别</th>
                    <th class="pb-2 pr-3">病害</th>
                    <th class="pb-2 pr-3">虫害</th>
                    <th class="pb-2 pr-3">处理率</th>
                    <th class="pb-2">状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="rpt in reports" :key="rpt.id" class="border-b border-white/5 hover:bg-white/5 transition-colors">
                    <td class="py-2 pr-3 font-mono text-xs text-white">{{ rpt.reportDate }}</td>
                    <td class="py-2 pr-3 font-mono text-xs text-slate-400">{{ rpt.summary?.totalDetections ?? 0 }}</td>
                    <td class="py-2 pr-3 font-mono text-xs text-sakura">{{ rpt.summary?.diseaseCount ?? 0 }}</td>
                    <td class="py-2 pr-3 font-mono text-xs text-amber">{{ rpt.summary?.pestCount ?? 0 }}</td>
                    <td class="py-2 pr-3 font-mono text-xs text-cyber-green">{{ ((rpt.summary?.workorderHandledRate ?? 0) * 100).toFixed(0) }}%</td>
                    <td class="py-2">
                      <span
                        class="px-2 py-0.5 rounded text-[10px] font-mono"
                        :class="rpt.reportDate === today
                          ? (todayGenerated ? 'text-cyber-green bg-cyber-green/10' : 'text-amber bg-amber/10')
                          : 'text-cyber-green bg-cyber-green/10'"
                      >
                        {{ rpt.reportDate === today ? (todayGenerated ? '已生成' : '未生成') : '已生成' }}
                      </span>
                    </td>
                  </tr>
                  <tr v-if="reports.length === 0">
                    <td colspan="6" class="py-8 text-center text-slate-500 text-sm">暂无报告数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </GlassCard>
        </div>
      </div>
    </template>
  </div>
</template>

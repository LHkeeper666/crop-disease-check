/**
 * 统计模块 API
 * 对接后端 /api/statistics 接口
 */
import { request } from './request'

const BASE = '/api/statistics'

/** 类型分布 */
export interface TypeDistribution {
  name: string
  value: number
}

/** 每日趋势 */
export interface DailyTrend {
  date: string
  diseaseCount: number
  pestCount: number
  count: number
}

/** Top 病虫害 */
export interface TopPest {
  name: string
  count: number
}

/** 网格热力图 */
export interface GridHeatmapItem {
  gridId: string
  gridLabel: string
  score: number
}

/** 统计概览 */
export interface StatisticsOverviewVO {
  totalReports: number
  todayReports: number
  pendingAudit: number
  processed: number
  highRiskAlerts: number
  typeDistribution: TypeDistribution[]
  diseaseDistribution: TypeDistribution[]
  pestDistribution: TypeDistribution[]
  dailyTrend: DailyTrend[]
  top5Pests: TopPest[]
  top5Diseases: TopPest[]
  gridHeatmap: GridHeatmapItem[]
}

/** 趋势统计 */
export interface TrendStatisticsVO {
  date: string
  diseaseCount: number
  pestCount: number
  total: number
}

/** 获取统计概览 */
export async function fetchStatisticsOverview(days?: number): Promise<StatisticsOverviewVO> {
  const query = days ? `?days=${days}` : ''
  return request<StatisticsOverviewVO>(`${BASE}/overview${query}`)
}

/** 获取趋势数据 */
export async function fetchStatisticsTrend(days?: number, granularity?: string): Promise<TrendStatisticsVO[]> {
  const params = new URLSearchParams()
  if (days) params.set('days', String(days))
  if (granularity) params.set('granularity', granularity)
  const query = params.toString() ? `?${params.toString()}` : ''
  return request<TrendStatisticsVO[]>(`${BASE}/trend${query}`)
}

/** 获取网格统计 */
export async function fetchGridStatistics(days?: number) {
  const query = days ? `?days=${days}` : ''
  return request<any[]>(`${BASE}/grid${query}`)
}

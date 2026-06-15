/**
 * 日报模块 API
 * 对接后端 /api/daily-report 接口
 */
import { request } from './request'
import type { PageResult } from './request'

const BASE = '/api/daily-report'

/** 日报摘要 */
export interface DailyReportSummaryDTO {
  greenhouseId: string | null
  totalInspections: number
  totalDetections: number
  diseaseCount: number
  pestCount: number
  workorderHandledRate: number
  topGrids: { gridLabel: string; count: number }[]
  topPests: { name: string; count: number }[]
}

/** 日报列表项 */
export interface DailyReportVO {
  id: string
  reportDate: string
  summary: DailyReportSummaryDTO
  emailSent: number
  emailSentAt: string | null
  createdAt: string
}

/** 日报详情 */
export interface DailyReportDetailVO {
  id: string
  reportDate: string
  summaryJson: Record<string, any>
  htmlContent: string
  emailSent: number
  emailSentAt: string | null
  createdAt: string
}

/** 获取日报列表（分页） */
export async function fetchDailyReports(params: {
  startDate?: string
  endDate?: string
  page?: number
  size?: number
} = {}): Promise<PageResult<DailyReportVO>> {
  const query = new URLSearchParams()
  if (params.startDate) query.set('startDate', params.startDate)
  if (params.endDate) query.set('endDate', params.endDate)
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  return request<PageResult<DailyReportVO>>(`${BASE}/list?${query.toString()}`)
}

/** 获取日报详情 */
export async function fetchDailyReportDetail(id: string): Promise<DailyReportDetailVO> {
  return request<DailyReportDetailVO>(`${BASE}/${id}`)
}

/** 生成日报 */
export async function generateDailyReport(date: string, greenhouseId?: string): Promise<string> {
  return request<string>(`${BASE}/generate`, {
    method: 'POST',
    body: JSON.stringify({ date, greenhouseId }),
  })
}

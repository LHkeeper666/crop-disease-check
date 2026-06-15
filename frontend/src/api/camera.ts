/**
 * 摄像头模块 API
 * 对接后端 /api/camera 接口
 */
import { request } from './request'
import type { PageResult } from './request'

const BASE = '/api/camera'

/** 摄像头实体 */
export interface CameraVO {
  id: string
  name: string
  rtspUrl: string
  rtspUrlSub: string | null
  locationX: number
  locationY: number
  direction: number
  captureResolution: string
  captureQuality: number
  reconnectInterval: number
  status: 'ONLINE' | 'OFFLINE' | 'FAULT'
  lastFrameAt: string | null
  lastOnlineAt: string | null
  companyId: string
  createdAt: string
  updatedAt: string
}

/** 获取摄像头列表（分页） */
export async function fetchCameras(params: {
  status?: string
  keyword?: string
  page?: number
  size?: number
} = {}): Promise<PageResult<CameraVO>> {
  const query = new URLSearchParams()
  if (params.status) query.set('status', params.status)
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  return request<PageResult<CameraVO>>(`${BASE}/list?${query.toString()}`)
}

/** 更新摄像头 */
export async function updateCamera(id: string, data: {
  name?: string
  rtspUrl?: string
  rtspUrlSub?: string
  locationX?: number
  locationY?: number
  direction?: number
  coverageGrids?: string[]
  captureResolution?: string
  captureQuality?: number
  reconnectInterval?: number
}): Promise<void> {
  return request<void>(`${BASE}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 删除摄像头 */
export async function deleteCamera(id: string): Promise<void> {
  return request<void>(`${BASE}/${id}`, { method: 'DELETE' })
}

/** 创建摄像头 */
export async function createCamera(data: {
  name: string
  rtspUrl: string
  rtspUrlSub?: string
  locationX?: number
  locationY?: number
  direction?: number
  coverageGrids?: string[]
  captureResolution?: string
  captureQuality?: number
  reconnectInterval?: number
}): Promise<{ id: string }> {
  return request<{ id: string }>(BASE, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

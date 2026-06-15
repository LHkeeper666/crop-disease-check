/**
 * 网格模块 API
 * 对接后端 /api/grid 接口
 */
import { request } from './request'

const BASE = '/api/grid'

/** 网格 VO */
export interface GridVO {
  id: string
  label: string
  greenhouseId: string | null
  polygonCoords: string | null
  areaM2: number | null
  cropType: string | null
  createdAt: string
}

/** 获取网格列表 */
export async function fetchGrids(greenhouseId?: string): Promise<GridVO[]> {
  const query = greenhouseId ? `?greenhouseId=${greenhouseId}` : ''
  return request<GridVO[]>(`${BASE}/list${query}`)
}

/** 更新网格 */
export async function updateGrid(id: string, data: {
  label?: string
  greenhouseId?: string
  polygonCoords?: { x: number; y: number }[]
  cropType?: string
}): Promise<void> {
  return request<void>(`${BASE}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 删除网格 */
export async function deleteGrid(id: string): Promise<void> {
  return request<void>(`${BASE}/${id}`, { method: 'DELETE' })
}

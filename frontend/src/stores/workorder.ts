import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  fetchWorkOrders,
  createManualWorkOrder as apiCreateManual,
  updateWorkOrderStatus as apiUpdateStatus,
  updateWorkOrderSeverity as apiUpdateSeverity,
  deleteWorkOrder as apiDelete,
  type WorkOrderVO,
  type WorkOrderManualCreateDTO,
} from '../api/workorder'

// 前端工单类型，与后端 WorkOrderVO 对齐
export interface WorkOrder {
  id: number
  title: string
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'IGNORED'
  type: 'disease' | 'pest'
  gridLabel: string
  pestName: string
  confidence: number
  assignedToName: string
  createdAt: string
  updatedAt: string
}

const severityLevel: Record<string, number> = {
  CRITICAL: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
}

/** 将后端 VO 转为前端类型 */
function fromVO(vo: WorkOrderVO): WorkOrder {
  return {
    id: vo.id,
    title: vo.title,
    severity: vo.severity as WorkOrder['severity'],
    status: vo.status as WorkOrder['status'],
    type: vo.type as WorkOrder['type'],
    gridLabel: vo.gridLabel,
    pestName: vo.pestName,
    confidence: vo.confidence,
    assignedToName: vo.assignedToName || '未分配',
    createdAt: vo.createdAt,
    updatedAt: vo.updatedAt,
  }
}

export const useWorkOrderStore = defineStore('workorder', () => {
  const orders = ref<WorkOrder[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 每个网格最高危险等级（未完成且未忽略的工单）
  const gridSeverityMap = computed(() => {
    const map: Record<string, string> = {}
    for (const o of orders.value) {
      if (o.status === 'DONE' || o.status === 'IGNORED') continue
      const cur = map[o.gridLabel]
      if (!cur || severityLevel[o.severity] > severityLevel[cur]) {
        map[o.gridLabel] = o.severity
      }
    }
    return map
  })

  // 报警列表（未完成且未忽略的工单）
  const alerts = computed(() => {
    return orders.value
      .filter(o => o.status !== 'DONE' && o.status !== 'IGNORED')
      .sort((a, b) => severityLevel[b.severity] - severityLevel[a.severity])
      .map(o => ({
        id: o.id,
        severity: o.severity,
        message: `Grid-${o.gridLabel} ${o.pestName} 置信度 ${(o.confidence * 100).toFixed(0)}%`,
        time: o.createdAt.replace('T', ' ').slice(0, 16),
      }))
  })

  // 7日趋势数据（病害 vs 虫害，按创建日期统计）
  const trendData = computed(() => {
    const dates: string[] = []
    const now = new Date()
    for (let i = 6; i >= 0; i--) {
      const d = new Date(now)
      d.setDate(d.getDate() - i)
      dates.push(d.toISOString().slice(0, 10))
    }

    const disease = dates.map(date =>
      orders.value.filter(o => o.type === 'disease' && o.createdAt.slice(0, 10) === date).length
    )
    const pest = dates.map(date =>
      orders.value.filter(o => o.type === 'pest' && o.createdAt.slice(0, 10) === date).length
    )

    return {
      dates: dates.map(d => d.slice(5)), // MM-DD
      disease,
      pest,
    }
  })

  /** 从后端加载工单列表 */
  async function fetchOrders(params?: { status?: string; severity?: string }) {
    loading.value = true
    error.value = null
    try {
      const page = await fetchWorkOrders({
        ...params,
        page: 1,
        size: 200, // 一次拉取足够多，前端暂不做分页
      })
      orders.value = page.records.map(fromVO)
    } catch (e: any) {
      error.value = e.message || '加载工单失败'
      console.error('[workorder] fetchOrders error:', e)
    } finally {
      loading.value = false
    }
  }

  /** 手动创建工单（调用后端API） */
  async function addOrder(dto: WorkOrderManualCreateDTO) {
    loading.value = true
    error.value = null
    try {
      await apiCreateManual(dto)
      // 创建成功后刷新列表
      await fetchOrders()
    } catch (e: any) {
      error.value = e.message || '创建工单失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 删除工单 */
  async function removeOrder(id: number) {
    loading.value = true
    error.value = null
    try {
      await apiDelete(id)
      orders.value = orders.value.filter(o => o.id !== id)
    } catch (e: any) {
      error.value = e.message || '删除工单失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 更新工单状态 */
  async function updateOrderStatus(id: number, status: WorkOrder['status'], comment?: string) {
    error.value = null
    try {
      await apiUpdateStatus(id, status, comment)
      const o = orders.value.find(o => o.id === id)
      if (o) {
        o.status = status
        o.updatedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
      }
    } catch (e: any) {
      error.value = e.message || '更新状态失败'
      throw e
    }
  }

  /** 升级严重程度 */
  async function escalateSeverity(id: number) {
    const o = orders.value.find(o => o.id === id)
    if (!o) return
    const levels = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
    const idx = levels.indexOf(o.severity)
    if (idx >= levels.length - 1) return
    const newSeverity = levels[idx + 1]
    error.value = null
    try {
      await apiUpdateSeverity(id, newSeverity)
      o.severity = newSeverity as WorkOrder['severity']
      o.updatedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
    } catch (e: any) {
      error.value = e.message || '升级等级失败'
      throw e
    }
  }

  /** 降级严重程度 */
  async function deescalateSeverity(id: number) {
    const o = orders.value.find(o => o.id === id)
    if (!o) return
    const levels = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
    const idx = levels.indexOf(o.severity)
    if (idx <= 0) return
    const newSeverity = levels[idx - 1]
    error.value = null
    try {
      await apiUpdateSeverity(id, newSeverity)
      o.severity = newSeverity as WorkOrder['severity']
      o.updatedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
    } catch (e: any) {
      error.value = e.message || '降级等级失败'
      throw e
    }
  }

  return {
    orders,
    loading,
    error,
    gridSeverityMap,
    alerts,
    trendData,
    fetchOrders,
    addOrder,
    removeOrder,
    updateOrderStatus,
    escalateSeverity,
    deescalateSeverity,
  }
})

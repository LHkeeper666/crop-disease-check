import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { mockWorkOrders } from '../mock/data'

export interface WorkOrder {
  id: string
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

export const useWorkOrderStore = defineStore('workorder', () => {
  const orders = ref<WorkOrder[]>([...mockWorkOrders as WorkOrder[]])

  // 每个网格最高危险等级（仅统计活跃工单: PENDING / PROCESSING）
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

  // 报警列表（来源于活跃工单）
  const alerts = computed(() => {
    return orders.value
      .filter(o => o.status === 'PENDING' || o.status === 'PROCESSING')
      .sort((a, b) => severityLevel[b.severity] - severityLevel[a.severity])
      .slice(0, 6)
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

  function addOrder(order: WorkOrder) {
    orders.value.unshift(order)
  }

  function removeOrder(id: string) {
    orders.value = orders.value.filter(o => o.id !== id)
  }

  function updateOrderStatus(id: string, status: WorkOrder['status']) {
    const o = orders.value.find(o => o.id === id)
    if (o) {
      o.status = status
      o.updatedAt = new Date().toISOString()
    }
  }

  function escalateSeverity(id: string) {
    const o = orders.value.find(o => o.id === id)
    if (!o) return
    const levels = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
    const idx = levels.indexOf(o.severity)
    if (idx < levels.length - 1) {
      o.severity = levels[idx + 1] as WorkOrder['severity']
      o.updatedAt = new Date().toISOString()
    }
  }

  function deescalateSeverity(id: string) {
    const o = orders.value.find(o => o.id === id)
    if (!o) return
    const levels = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
    const idx = levels.indexOf(o.severity)
    if (idx > 0) {
      o.severity = levels[idx - 1] as WorkOrder['severity']
      o.updatedAt = new Date().toISOString()
    }
  }

  return {
    orders,
    gridSeverityMap,
    alerts,
    trendData,
    addOrder,
    removeOrder,
    updateOrderStatus,
    escalateSeverity,
    deescalateSeverity,
  }
})

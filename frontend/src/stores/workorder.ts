import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  fetchWorkOrders,
  createManualWorkOrder as apiCreateManual,
  updateWorkOrderStatus as apiUpdateStatus,
  updateWorkOrderSeverity as apiUpdateSeverity,
  deleteWorkOrder as apiDelete,
  fetchExperts as apiFetchExperts,
  fetchManagers as apiFetchManagers,
  fetchStaff as apiFetchStaff,
  updateWorkOrderAssignee as apiUpdateAssignee,
  type WorkOrderVO,
  type WorkOrderManualCreateDTO,
  type ExpertVO,
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
  imageUrl: string | null
  originalImageUrl: string | null
  assignedToId: string
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
  // 规范化状态：ESCALATED 映射为 PROCESSING，未知状态回退为 PENDING
  const validStatuses: WorkOrder['status'][] = ['PENDING', 'PROCESSING', 'DONE', 'IGNORED']
  const rawStatus = vo.status === 'ESCALATED' ? 'PROCESSING' : vo.status
  const status = validStatuses.includes(rawStatus as WorkOrder['status'])
    ? (rawStatus as WorkOrder['status'])
    : 'PENDING'

  // 规范化严重程度
  const validSeverities: WorkOrder['severity'][] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  const severity = validSeverities.includes(vo.severity as WorkOrder['severity'])
    ? (vo.severity as WorkOrder['severity'])
    : 'MEDIUM'

  return {
    id: vo.id,
    title: vo.title,
    severity,
    status,
    type: vo.type as WorkOrder['type'],
    gridLabel: vo.gridLabel,
    pestName: vo.pestName,
    confidence: vo.confidence,
    imageUrl: vo.imageUrl || null,
    originalImageUrl: vo.originalImageUrl || null,
    assignedToId: vo.assignedTo || '',
    assignedToName: vo.assignedToName || '未分配',
    createdAt: vo.createdAt,
    updatedAt: vo.updatedAt,
  }
}

export const useWorkOrderStore = defineStore('workorder', () => {
  const orders = ref<WorkOrder[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 专家列表状态
  const experts = ref<ExpertVO[]>([])
  const expertsLoading = ref(false)
  const expertsError = ref<string | null>(null)

  // 管理员列表状态
  const managers = ref<ExpertVO[]>([])
  const managersLoading = ref(false)
  const managersError = ref<string | null>(null)

  // 基层员工列表状态
  const staffList = ref<ExpertVO[]>([])
  const staffLoading = ref(false)
  const staffError = ref<string | null>(null)

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

  // 报警列表（未完成且未忽略的工单，支持置信度阈值过滤）
  function getAlerts(minConfidence = 0) {
    return orders.value
      .filter(o => o.status !== 'DONE' && o.status !== 'IGNORED' && o.confidence >= minConfidence)
      .sort((a, b) => severityLevel[b.severity] - severityLevel[a.severity])
      .map(o => ({
        id: o.id,
        severity: o.severity,
        confidence: o.confidence,
        message: `Grid-${o.gridLabel} ${o.pestName} 置信度 ${(o.confidence * 100).toFixed(0)}%`,
        time: o.createdAt.replace('T', ' ').slice(0, 16),
      }))
  }

  // 默认不过滤的报警列表
  const alerts = computed(() => getAlerts())

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

  /** 从 VO 添加工单到本地 store（用于 WebSocket 实时推送） */
  function addOrderFromVO(vo: WorkOrderVO) {
    const idx = orders.value.findIndex(o => o.id === vo.id)
    if (idx === -1) {
      orders.value.unshift(fromVO(vo))
    }
  }

  /** 本地更新工单字段（用于 WebSocket 实时推送） */
  function updateOrderLocal(id: number, patch: Partial<Pick<WorkOrder, 'status' | 'severity'>>) {
    const o = orders.value.find(o => o.id === id)
    if (o) {
      if (patch.status) o.status = patch.status
      if (patch.severity) o.severity = patch.severity
      o.updatedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
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
  async function updateOrderStatus(id: number, status: WorkOrder['status'], comment?: string, expertComment?: string) {
    error.value = null
    try {
      await apiUpdateStatus(id, status, comment, expertComment)
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

  /** 获取专家列表 */
  async function fetchExperts() {
    expertsLoading.value = true
    expertsError.value = null
    try {
      const list = await apiFetchExperts()
      experts.value = list
    } catch (e: any) {
      expertsError.value = e.message || '加载专家列表失败'
      console.error('[workorder] fetchExperts error:', e)
    } finally {
      expertsLoading.value = false
    }
  }

  /** 获取管理员列表 */
  async function fetchManagers() {
    managersLoading.value = true
    managersError.value = null
    try {
      const list = await apiFetchManagers()
      managers.value = list
    } catch (e: any) {
      managersError.value = e.message || '加载管理员列表失败'
      console.error('[workorder] fetchManagers error:', e)
    } finally {
      managersLoading.value = false
    }
  }

  /** 获取基层员工列表 */
  async function fetchStaff() {
    staffLoading.value = true
    staffError.value = null
    try {
      const list = await apiFetchStaff()
      staffList.value = list
    } catch (e: any) {
      staffError.value = e.message || '加载基层员工列表失败'
      console.error('[workorder] fetchStaff error:', e)
    } finally {
      staffLoading.value = false
    }
  }

  /** 更新工单指派专家 */
  async function updateAssignee(id: number, assignedTo: string) {
    error.value = null
    try {
      await apiUpdateAssignee(id, assignedTo)
      const o = orders.value.find(o => o.id === id)
      if (o) {
        o.assignedToId = assignedTo
        // assignedToName 会在下次 fetchOrders 时更新
        o.updatedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
      }
    } catch (e: any) {
      error.value = e.message || '更新指派专家失败'
      throw e
    }
  }

  return {
    orders,
    loading,
    error,
    experts,
    expertsLoading,
    expertsError,
    managers,
    managersLoading,
    managersError,
    staffList,
    staffLoading,
    staffError,
    gridSeverityMap,
    alerts,
    getAlerts,
    trendData,
    fetchOrders,
    addOrder,
    addOrderFromVO,
    updateOrderLocal,
    removeOrder,
    updateOrderStatus,
    escalateSeverity,
    deescalateSeverity,
    fetchExperts,
    fetchManagers,
    fetchStaff,
    updateAssignee,
  }
})

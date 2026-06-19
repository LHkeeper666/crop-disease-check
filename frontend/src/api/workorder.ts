/**
 * 工单模块 API 服务层
 * 对接后端 /api/workorder 接口
 */

const BASE = '/api/workorder'

/** 后端统一响应格式 */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 工单列表项 */
export interface WorkOrderVO {
  id: number
  title: string
  severity: string
  status: string
  type: string
  gridLabel: string
  pestName: string
  confidence: number
  imageUrl: string | null
  originalImageUrl: string | null
  assignedTo: string
  assignedToName: string
  createdAt: string
  updatedAt: string
}

/** 工单详情 */
export interface WorkOrderDetailVO extends WorkOrderVO {
  inferenceId: string
  expertComment: string | null
  assignedToEmail: string | null
  statusHistory: StatusHistoryVO[]
}

/** 状态历史 */
export interface StatusHistoryVO {
  status: string
  createdAt: string
  operator: string
}

/** 回调响应 */
export interface CallbackResponseVO {
  workorderId: string
  newStatus: string
}

/** 创建工单请求 */
export interface WorkOrderCreateDTO {
  inferenceId: string
  severity: string
  assignedTo?: string
}

/** 邮件预览响应 */
export interface EmailPreviewVO {
  toUserId: string
  toName: string
  toEmail: string
  subject: string
  content: string
}

/** 手动创建工单请求 */
export interface WorkOrderManualCreateDTO {
  title: string
  severity: string
  type?: string
  gridLabel?: string
  pestName?: string
  confidence?: number
  assignedTo?: string
  imageUrl?: string
  originalImageUrl?: string
}

/** 通用请求头 */
function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token') || ''
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/** 统一请求封装 */
async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: { ...authHeaders(), ...(options.headers as Record<string, string> || {}) },
  })
  const json: ApiResult<T> = await res.json()
  if (json.code !== 200) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

/** 获取工单列表（分页） */
export async function fetchWorkOrders(params: {
  status?: string
  severity?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
} = {}): Promise<PageResult<WorkOrderVO>> {
  const query = new URLSearchParams()
  if (params.status) query.set('status', params.status)
  if (params.severity) query.set('severity', params.severity)
  if (params.startDate) query.set('startDate', params.startDate)
  if (params.endDate) query.set('endDate', params.endDate)
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))

  return request<PageResult<WorkOrderVO>>(`${BASE}/list?${query.toString()}`)
}

/** 获取工单详情 */
export async function fetchWorkOrderDetail(id: string): Promise<WorkOrderDetailVO> {
  return request<WorkOrderDetailVO>(`${BASE}/${id}`)
}

/** 创建工单（基于推理记录） */
export async function createWorkOrder(dto: WorkOrderCreateDTO): Promise<number> {
  return request<number>(`${BASE}/create`, {
    method: 'POST',
    body: JSON.stringify(dto),
  })
}

/** 手动创建工单（不依赖推理记录） */
export async function createManualWorkOrder(dto: WorkOrderManualCreateDTO): Promise<number> {
  return request<number>(`${BASE}/create-manual`, {
    method: 'POST',
    body: JSON.stringify(dto),
  })
}

/** 更新工单状态 */
export async function updateWorkOrderStatus(
  id: number,
  status: string,
  comment?: string,
  expertComment?: string
): Promise<void> {
  return request<void>(`${BASE}/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status, comment, expertComment }),
  })
}

/** 更新工单严重程度 */
export async function updateWorkOrderSeverity(
  id: number,
  severity: string
): Promise<void> {
  return request<void>(`${BASE}/${id}/severity`, {
    method: 'PUT',
    body: JSON.stringify({ severity }),
  })
}

/** 删除工单 */
export async function deleteWorkOrder(id: number): Promise<void> {
  return request<void>(`${BASE}/${id}`, {
    method: 'DELETE',
  })
}

/** 预览工单邮件（AI 自动生成内容） */
export async function previewWorkOrderEmail(id: number): Promise<EmailPreviewVO> {
  return request<EmailPreviewVO>(`${BASE}/${id}/preview-email`, {
    method: 'POST',
  })
}

/** 专家用户信息 */
export interface ExpertVO {
  id: string
  name: string
  email: string | null
  phone: string | null
  role: string
}

/** 获取专家列表（所有登录用户可访问） */
export async function fetchExperts(): Promise<ExpertVO[]> {
  return request<ExpertVO[]>('/api/users/experts')
}

/** 获取管理员列表（所有登录用户可访问） */
export async function fetchManagers(): Promise<ExpertVO[]> {
  return request<ExpertVO[]>('/api/users/managers')
}

/** 获取基层员工列表（所有登录用户可访问） */
export async function fetchStaff(): Promise<ExpertVO[]> {
  return request<ExpertVO[]>('/api/users/staff')
}

/** 更新工单指派专家 */
export async function updateWorkOrderAssignee(
  id: number,
  assignedTo: string
): Promise<void> {
  return request<void>(`${BASE}/${id}/assignee`, {
    method: 'PUT',
    body: JSON.stringify({ assignedTo }),
  })
}

/**
 * 页面上下文 — 前端传标识，后端查数据库构建完整上下文
 * 与后端 PageContext.java / VisibleData.java 对齐
 */
export interface PageContext {
  /** 路由路径，如 "/workorders" */
  page: string
  /** 页面名称，如 "工单管理" */
  pageName: string
  /** 当前选中的资源 ID */
  selectedId?: string
  /** 页面可见数据摘要 */
  visibleData?: VisibleData
}

export interface VisibleData {
  /** 列表数据（前 N 条，建议最多 5 条） */
  list?: Record<string, any>[]
  /** 统计数据（关键指标） */
  stats?: Record<string, any>
  /** 当前筛选条件 */
  filters?: Record<string, string>
  /** 其他页面特定数据 */
  extra?: Record<string, any>
}

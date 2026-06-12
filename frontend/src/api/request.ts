/**
 * 通用请求封装
 * 对接后端统一响应格式 { code, message, data }
 */

/** 后端统一响应格式 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 通用请求头 */
export function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token') || ''
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/** 统一请求封装 */
export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
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

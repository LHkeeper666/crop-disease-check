/**
 * 用户模块 API
 * 对接后端 /api/users 接口
 */
import { request } from './request'
import type { PageResult } from './request'

const BASE = '/api/users'

/** 用户简要信息 */
export interface UserSimpleVO {
  id: string
  username: string
  name: string
  role: string
  phone: string | null
  email: string | null
  status: string
  lastLoginAt: string | null
  createdAt: string
}

/** 用户详细信息 */
export interface UserVO {
  id: string
  username: string
  name: string
  role: string
  phone: string | null
  email: string | null
  avatar: string | null
  companyId: string | null
  approved: boolean
  status: string
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** 获取用户列表（分页，ADMIN） */
export async function fetchUsers(params: {
  page?: number
  size?: number
  keyword?: string
  role?: string
  status?: string
} = {}): Promise<PageResult<UserSimpleVO>> {
  const query = new URLSearchParams()
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.role) query.set('role', params.role)
  if (params.status) query.set('status', params.status)
  return request<PageResult<UserSimpleVO>>(`${BASE}?${query.toString()}`)
}

/** 获取当前用户信息 */
export async function fetchCurrentUser(): Promise<UserVO> {
  return request<UserVO>(`${BASE}/me`)
}

/** 更新用户信息（ADMIN） */
export async function updateUser(id: string, data: {
  name?: string
  phone?: string
  email?: string
  role?: string
  status?: string
}): Promise<UserVO> {
  return request<UserVO>(`${BASE}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 更新用户状态（ADMIN） */
export async function updateUserStatus(id: string, status: string): Promise<void> {
  return request<void>(`${BASE}/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}

/** 重置用户密码（ADMIN） */
export async function resetUserPassword(id: string, newPassword: string): Promise<void> {
  return request<void>(`${BASE}/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ newPassword }),
  })
}

/** 删除用户（ADMIN） */
export async function deleteUser(id: string): Promise<void> {
  return request<void>(`${BASE}/${id}`, { method: 'DELETE' })
}

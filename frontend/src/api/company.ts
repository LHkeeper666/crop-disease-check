/**
 * 企业模块 API
 * 对接后端 /api/company 接口
 */
import { request } from './request'

const BASE = '/api/company'

/** 验证邀请码结果 */
export interface ValidateInviteVO {
  valid: boolean
  companyName: string
}

/** 加入企业结果 */
export interface JoinCompanyVO {
  companyId: string
  companyName: string
}

/** 验证邀请码（公开接口） */
export async function validateInviteCode(inviteCode: string): Promise<ValidateInviteVO> {
  return request<ValidateInviteVO>(`${BASE}/validate-invite`, {
    method: 'POST',
    body: JSON.stringify({ inviteCode }),
  })
}

/** 加入企业（需登录） */
export async function joinCompany(inviteCode: string): Promise<JoinCompanyVO> {
  return request<JoinCompanyVO>(`${BASE}/join`, {
    method: 'POST',
    body: JSON.stringify({ inviteCode }),
  })
}

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { validateInviteCode as apiValidateInvite, joinCompany as apiJoinCompany } from '../api/company'

export interface UserInfo {
  id: string
  username: string
  name: string
  role: 'ADMIN' | 'EXPERT' | 'MANAGER'
  phone: string
  email: string
  avatar?: string
  companyId: string
  approved: boolean
}

// 企业邀请码 → 企业信息（本地 mock 回退用）
const companyInvites: Record<string, { companyId: string; companyName: string }> = {
  TF2026: { companyId: 'company-001', companyName: 'TreeForge 智慧农场' },
  AG2026: { companyId: 'company-002', companyName: '绿丰农业科技' },
}

// Registered users database (simulates backend)
const registeredUsersDB = ref<UserInfo[]>([])

// Mock approved users (simulates existing users in the system)
const mockApprovedUsers = ref<UserInfo[]>([
  { id: 'u-001', username: 'admin', name: '系统管理员', role: 'ADMIN', phone: '13800138000', email: 'admin@treeforge.cn', companyId: 'company-001', approved: true },
  { id: 'u-002', username: 'expert_li', name: '李专家', role: 'EXPERT', phone: '13900139000', email: '2043412933@qq.com', companyId: 'company-001', approved: true },
  { id: 'u-003', username: 'expert_wang', name: '王专家', role: 'EXPERT', phone: '13700137000', email: '1367858987@qq.com', companyId: 'company-001', approved: true },
  { id: 'u-004', username: 'manager_zhang', name: '张三', role: 'MANAGER', phone: '13600136000', email: 'zhang@treeforge.cn', companyId: 'company-001', approved: true },
])

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('treeforge_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => userInfo.value?.name || '用户')
  const userRole = computed(() => userInfo.value?.role || 'MANAGER')
  const isApproved = computed(() => userInfo.value?.approved ?? false)

  async function login(username: string, password: string): Promise<{ success: boolean; pending?: boolean; message?: string }> {
    if (!username || !password) return { success: false, message: '请输入用户名和密码' }

    // 优先调用真实后端登录接口
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      const data = await res.json()
      if (data.code === 200) {
        const { token: jwtToken, userInfo: user } = data.data
        token.value = jwtToken
        userInfo.value = {
          id: user.id,
          username: user.username,
          name: user.name,
          role: user.role || 'MANAGER',
          phone: user.phone || '',
          email: user.email || '',
          avatar: user.avatar,
          companyId: user.companyId || '',
          approved: user.approved ?? true,
        }
        localStorage.setItem('treeforge_token', jwtToken)
        return { success: true }
      }
      return { success: false, message: data.message || '登录失败' }
    } catch {
      // 后端不可用时回退到本地 mock 登录（离线开发模式）
      console.warn('[auth] 后端不可用，使用本地 mock 登录')
    }

    // --- 本地 mock 回退 ---
    await new Promise(r => setTimeout(r, 300))

    const registered = registeredUsersDB.value.find(u => u.username === username)
    if (registered) {
      token.value = 'mock_jwt_token_' + Date.now()
      userInfo.value = { ...registered }
      localStorage.setItem('treeforge_token', token.value)
      return { success: true, pending: !registered.approved }
    }

    const mockUser = mockApprovedUsers.value.find(u => u.username === username)
    if (mockUser) {
      token.value = 'mock_jwt_token_' + Date.now()
      userInfo.value = { ...mockUser }
      localStorage.setItem('treeforge_token', token.value)
      return { success: true }
    }

    const newUser: UserInfo = {
      id: 'u-' + Date.now(),
      username,
      name: username,
      role: 'MANAGER',
      phone: '',
      email: '',
      companyId: '',
      approved: false,
    }
    registeredUsersDB.value.push(newUser)
    token.value = 'mock_jwt_token_' + Date.now()
    userInfo.value = { ...newUser }
    localStorage.setItem('treeforge_token', token.value)
    return { success: true, pending: true }
  }

  // Validate invitation code and approve user
  async function validateInviteCode(code: string): Promise<{ success: boolean; companyName?: string; message: string }> {
    // 优先调用后端 API
    try {
      const result = await apiValidateInvite(code)
      if (result.valid) {
        return { success: true, companyName: result.companyName, message: '' }
      }
      return { success: false, message: '邀请码无效' }
    } catch {
      // 后端不可用时回退到本地 mock
      console.warn('[auth] 后端不可用，使用本地邀请码验证')
    }
    const company = companyInvites[code]
    if (!company) {
      return { success: false, message: '邀请码无效，请联系企业管理员获取' }
    }
    return { success: true, companyName: company.companyName, message: '' }
  }

  // Join company via invitation code
  async function joinCompany(code: string): Promise<boolean> {
    // 优先调用后端 API
    try {
      const result = await apiJoinCompany(code)
      if (userInfo.value) {
        userInfo.value.companyId = result.companyId
        userInfo.value.approved = true
      }
      localStorage.removeItem('treeforge_user_pending')
      return true
    } catch {
      // 后端不可用时回退到本地 mock
      console.warn('[auth] 后端不可用，使用本地加入企业')
    }
    const company = companyInvites[code]
    if (!company || !userInfo.value) return false

    // Update current user
    userInfo.value.approved = true
    userInfo.value.companyId = company.companyId

    // Update in DB
    const user = registeredUsersDB.value.find(u => u.id === userInfo.value?.id)
    if (user) {
      user.approved = true
      user.companyId = company.companyId
    }

    localStorage.removeItem('treeforge_user_pending')
    return true
  }

  // 发送邮箱验证码（调用真实后端API）
  async function sendOtp(email: string): Promise<{ ok: boolean; message?: string }> {
    try {
      const res = await fetch('/api/auth/send-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, type: 'REGISTER' }),
      })
      const data = await res.json()
      if (data.code === 200) {
        return { ok: true }
      }
      return { ok: false, message: data.message || '发送验证码失败' }
    } catch {
      return { ok: false, message: '网络错误，请稍后重试' }
    }
  }

  // 用户注册（调用真实后端API）
  async function register(email: string, username: string, password: string, code: string): Promise<{ ok: boolean; message?: string }> {
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, username, password, code }),
      })
      const data = await res.json()
      if (data.code === 200) {
        return { ok: true }
      }
      return { ok: false, message: data.message || '注册失败' }
    } catch {
      return { ok: false, message: '网络错误，请稍后重试' }
    }
  }

  // Get all users for a company (admin user management)
  function getCompanyUsers(companyId: string): UserInfo[] {
    return mockApprovedUsers.value.filter(u => u.companyId === companyId)
  }

  // Get all registered + approved users
  function getAllUsers(): UserInfo[] {
    return [...mockApprovedUsers.value, ...registeredUsersDB.value.filter(u => u.approved)]
  }

  // Get pending users (not approved)
  function getPendingUsers(): UserInfo[] {
    return registeredUsersDB.value.filter(u => !u.approved)
  }

  // Update user (edit)
  function updateUser(userId: string, updates: Partial<UserInfo>) {
    const user = mockApprovedUsers.value.find(u => u.id === userId) || registeredUsersDB.value.find(u => u.id === userId)
    if (user) {
      Object.assign(user, updates)
      // Also update userInfo if editing self
      if (userInfo.value?.id === userId) {
        Object.assign(userInfo.value, updates)
      }
    }
  }

  // Toggle user status
  function toggleUserStatus(userId: string) {
    const user = mockApprovedUsers.value.find(u => u.id === userId) || registeredUsersDB.value.find(u => u.id === userId)
    if (user) {
      user.approved = !user.approved
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('treeforge_token')
    localStorage.removeItem('treeforge_user_pending')
  }

  // Restore session from localStorage
  if (token.value && !userInfo.value) {
    userInfo.value = {
      id: 'u-001',
      username: 'admin',
      name: '系统管理员',
      role: 'ADMIN',
      phone: '13800138000',
      email: 'admin@treeforge.cn',
      companyId: 'company-001',
      approved: true,
    }
  }

  return {
    token, userInfo, isLoggedIn, userName, userRole, isApproved,
    login, register, validateInviteCode, joinCompany,
    getCompanyUsers, getAllUsers, getPendingUsers, updateUser, toggleUserStatus,
    sendOtp, logout,
  }
})

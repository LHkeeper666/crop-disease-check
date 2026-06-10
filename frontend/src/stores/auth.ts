import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

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

// 企业邀请码 → 企业信息
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

  async function login(username: string, password: string): Promise<{ success: boolean; pending?: boolean }> {
    if (!username || !password) return { success: false }
    await new Promise(r => setTimeout(r, 800))

    // Check registered users
    const registered = registeredUsersDB.value.find(u => u.username === username)
    if (registered) {
      token.value = 'mock_jwt_token_' + Date.now()
      userInfo.value = { ...registered }
      localStorage.setItem('treeforge_token', token.value)
      return { success: true, pending: !registered.approved }
    }

    // Check mock approved users
    const mockUser = mockApprovedUsers.value.find(u => u.username === username)
    if (mockUser) {
      token.value = 'mock_jwt_token_' + Date.now()
      userInfo.value = { ...mockUser }
      localStorage.setItem('treeforge_token', token.value)
      return { success: true }
    }

    // Unknown user → register as pending
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
  function validateInviteCode(code: string): { success: boolean; companyName?: string; message: string } {
    const company = companyInvites[code]
    if (!company) {
      return { success: false, message: '邀请码无效，请联系企业管理员获取' }
    }
    return { success: true, companyName: company.companyName, message: '' }
  }

  // Join company via invitation code
  function joinCompany(code: string): boolean {
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

  // Demo mode: generate mock OTP
  const mockOtp = ref('')
  const showMockOtp = ref(false)

  async function sendOtp(_email: string): Promise<boolean> {
    await new Promise(r => setTimeout(r, 500))
    mockOtp.value = String(Math.floor(100000 + Math.random() * 900000))
    showMockOtp.value = true
    setTimeout(() => { showMockOtp.value = false }, 30000)
    return true
  }

  function hideMockOtp() {
    showMockOtp.value = false
  }

  // Register a new user
  async function register(email: string, username: string, _password: string): Promise<boolean> {
    await new Promise(r => setTimeout(r, 1000))
    if (registeredUsersDB.value.find(u => u.username === username)) return false
    if (mockApprovedUsers.value.find(u => u.username === username)) return false

    const newUser: UserInfo = {
      id: 'u-' + Date.now(),
      username,
      name: username,
      role: 'MANAGER',
      phone: '',
      email,
      companyId: '',
      approved: false,
    }
    registeredUsersDB.value.push(newUser)
    return true
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
    sendOtp, logout, mockOtp, showMockOtp, hideMockOtp,
  }
})

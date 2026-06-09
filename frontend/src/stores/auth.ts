import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  id: string
  username: string
  name: string
  role: 'ADMIN' | 'EXPERT' | 'MANAGER' | 'VISITOR'
  phone: string
  email: string
  avatar?: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('treeforge_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => userInfo.value?.name || '用户')
  const userRole = computed(() => userInfo.value?.role || 'VISITOR')

  async function login(username: string, password: string): Promise<boolean> {
    if (!username || !password) return false
    await new Promise(r => setTimeout(r, 800))

    token.value = 'mock_jwt_token_' + Date.now()
    userInfo.value = {
      id: 'uuid-001',
      username,
      name: username === 'admin' ? '系统管理员' : username,
      role: username === 'admin' ? 'ADMIN' : 'MANAGER',
      phone: '13800138000',
      email: 'admin@treeforge.cn',
    }
    localStorage.setItem('treeforge_token', token.value)
    return true
  }

  // Demo mode: generate mock OTP and display on screen
  // TODO: 后端完成后替换为真实邮件发送
  const mockOtp = ref('')
  const showMockOtp = ref(false)

  async function sendOtp(email: string): Promise<boolean> {
    await new Promise(r => setTimeout(r, 500))
    // Generate 6-digit code for demo
    mockOtp.value = String(Math.floor(100000 + Math.random() * 900000))
    showMockOtp.value = true
    // Auto-hide after 30 seconds
    setTimeout(() => { showMockOtp.value = false }, 30000)
    return true
  }

  function hideMockOtp() {
    showMockOtp.value = false
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('treeforge_token')
  }

  if (token.value && !userInfo.value) {
    userInfo.value = {
      id: 'uuid-001',
      username: 'admin',
      name: '系统管理员',
      role: 'ADMIN',
      phone: '13800138000',
      email: 'admin@treeforge.cn',
    }
  }

  return { token, userInfo, isLoggedIn, userName, userRole, login, sendOtp, logout, mockOtp, showMockOtp, hideMockOtp }
})

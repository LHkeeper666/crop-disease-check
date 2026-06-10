<script setup lang="ts">
import { ref, computed } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import { useAuthStore } from '../stores/auth'
import { mockCameras, mockGridHeatmap } from '../mock/data'

const auth = useAuthStore()

const activeTab = ref<'cameras' | 'grids' | 'users'>('cameras')

const cameras = ref(mockCameras)
const grids = ref(mockGridHeatmap)

// Users from auth store (company-scoped)
const users = computed(() => auth.getAllUsers())

// Pending users from auth store
const pendingUsers = computed(() => auth.getPendingUsers())

function approveAsRole(userId: string, role: 'ADMIN' | 'EXPERT' | 'MANAGER') {
  auth.updateUser(userId, { approved: true, companyId: 'company-001', role })
}

const cameraSearch = ref('')
const userSearch = ref('')

const filteredCameras = computed(() => {
  if (!cameraSearch.value) return cameras.value
  const keyword = cameraSearch.value.toLowerCase()
  return cameras.value.filter(cam =>
    cam.name.toLowerCase().includes(keyword) ||
    cam.grid.toLowerCase().includes(keyword)
  )
})

// Camera edit modal
const showCameraModal = ref(false)
const editingCamera = ref<any>(null)
const cameraForm = ref({
  name: '', rtspUrl: '', rtspUrlSub: '',
  locationX: '', locationY: '', direction: '0',
  grid: '', captureResolution: '640x640',
  captureQuality: '85', reconnectInterval: '30',
})

function openEditCamera(cam: any) {
  editingCamera.value = cam
  cameraForm.value = {
    name: cam.name,
    rtspUrl: cam.rtspUrl,
    rtspUrlSub: cam.rtspUrlSub || '',
    locationX: String(cam.locationX || ''),
    locationY: String(cam.locationY || ''),
    direction: String(cam.direction || 0),
    grid: cam.grid,
    captureResolution: cam.captureResolution || '640x640',
    captureQuality: String(cam.captureQuality || 85),
    reconnectInterval: String(cam.reconnectInterval || 30),
  }
  showCameraModal.value = true
}

function saveCamera() {
  if (!cameraForm.value.name.trim() || !cameraForm.value.rtspUrl.trim()) return
  const idx = cameras.value.findIndex(c => c.id === editingCamera.value.id)
  if (idx !== -1) {
    cameras.value[idx] = {
      ...cameras.value[idx],
      name: cameraForm.value.name,
      rtspUrl: cameraForm.value.rtspUrl,
      rtspUrlSub: cameraForm.value.rtspUrlSub,
      locationX: parseFloat(cameraForm.value.locationX) || 0,
      locationY: parseFloat(cameraForm.value.locationY) || 0,
      direction: parseFloat(cameraForm.value.direction) || 0,
      grid: cameraForm.value.grid,
      captureResolution: cameraForm.value.captureResolution,
      captureQuality: parseInt(cameraForm.value.captureQuality) || 85,
      reconnectInterval: parseInt(cameraForm.value.reconnectInterval) || 30,
    }
  }
  showCameraModal.value = false
  editingCamera.value = null
}

function closeCameraModal() {
  showCameraModal.value = false
  editingCamera.value = null
}

const filteredUsers = computed(() => {
  if (!userSearch.value) return users.value
  const keyword = userSearch.value.toLowerCase()
  return users.value.filter(user =>
    user.username.toLowerCase().includes(keyword) ||
    user.name.toLowerCase().includes(keyword)
  )
})

const statusColors: Record<string, string> = {
  ONLINE: 'text-cyber-green bg-cyber-green/10 border-cyber-green/20',
  OFFLINE: 'text-slate-400 bg-white/5 border-white/10',
  FAULT: 'text-sakura bg-sakura/10 border-sakura/20',
}

const roleLabels: Record<string, string> = {
  ADMIN: '管理员',
  EXPERT: '农技专家',
  MANAGER: '员工',
}

// Edit user modal
const showEditModal = ref(false)
const editingUser = ref<any>(null)
const editForm = ref({ name: '', role: '', phone: '', email: '' })

function openEdit(user: any) {
  editingUser.value = user
  editForm.value = { name: user.name, role: user.role, phone: user.phone || '', email: user.email || '' }
  showEditModal.value = true
}

function saveEdit() {
  if (!editingUser.value) return
  auth.updateUser(editingUser.value.id, {
    name: editForm.value.name,
    role: editForm.value.role as any,
    phone: editForm.value.phone,
    email: editForm.value.email,
  })
  showEditModal.value = false
  editingUser.value = null
}

function closeEdit() {
  showEditModal.value = false
  editingUser.value = null
}

// Reset password
const showResetConfirm = ref(false)
const resettingUser = ref<any>(null)

function openResetConfirm(user: any) {
  resettingUser.value = user
  showResetConfirm.value = true
}

function confirmResetPassword() {
  if (!resettingUser.value) return
  // In real app, call backend API to reset password
  // Mock: just show success
  resettingUser.value = null
  showResetConfirm.value = false
  alert('密码已重置为默认密码：123456')
}

function closeResetConfirm() {
  showResetConfirm.value = false
  resettingUser.value = null
}

// Toggle disable/enable
function toggleUserStatus(user: any) {
  auth.toggleUserStatus(user.id)
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">多租户生物资产与设备管理舱</h1>
        <p class="text-xs text-slate-500 font-mono">多租户生物资产与设备管理</p>
      </div>
    </div>

    <!-- Tab bar -->
    <div class="flex gap-2 shrink-0">
      <button
        v-for="tab in [
          { key: 'cameras', label: '摄像头管理' },
          { key: 'grids', label: '网格区域' },
          { key: 'users', label: '用户管理' },
        ]"
        :key="tab.key"
        class="px-4 py-2 rounded-xl text-sm transition-all duration-200"
        :class="activeTab === tab.key ? 'bg-white/10 text-white border border-white/10' : 'text-slate-500 hover:text-white hover:bg-white/5'"
        @click="
          activeTab = tab.key as any;
          cameraSearch = '';
          userSearch = '';
        "
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Content -->
    <GlassCard class="flex-1 min-h-0 overflow-hidden flex flex-col">
      <!-- Cameras -->
      <div v-if="activeTab === 'cameras'" class="flex-1 overflow-y-auto">
      <div class="flex justify-between items-center mb-4">
        <span class="text-xs text-slate-400 font-mono">{{ filteredCameras.length }} 台设备</span>
        <div class="flex gap-3">
          <input
            v-model="cameraSearch"
            type="text"
            placeholder="搜索摄像头名称或网格"
            class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50"
          />
        </div>
      </div>
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs text-slate-500 uppercase tracking-wider border-b border-white/5">
              <th class="pb-3 pr-4">名称</th>
              <th class="pb-3 pr-4">状态</th>
              <th class="pb-3 pr-4">覆盖网格</th>
              <th class="pb-3 pr-4">RTSP 地址</th>
              <th class="pb-3">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cam in filteredCameras" :key="cam.id" class="border-b border-white/5 hover:bg-white/5 transition-colors">
              <td class="py-3 pr-4 text-white font-medium">{{ cam.name }}</td>
              <td class="py-3 pr-4">
                <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="statusColors[cam.status]">
                  {{ cam.status }}
                </span>
              </td>
              <td class="py-3 pr-4 text-slate-400 font-mono text-xs">{{ cam.grid }}</td>
              <td class="py-3 pr-4 text-slate-500 font-mono text-xs truncate max-w-48">{{ cam.rtspUrl }}</td>
              <td class="py-3">
                <button class="text-xs text-cyber-green hover:underline" @click.stop="openEditCamera(cam)">编辑</button>
              </td>
            </tr>
            <tr v-if="filteredCameras.length === 0">
              <td colspan="5" class="py-8 text-center text-slate-500 text-sm">
                没有找到匹配的摄像头
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Grids -->
      <div v-if="activeTab === 'grids'" class="flex-1 overflow-y-auto">
        <div class="flex justify-between items-center mb-4">
          <span class="text-xs text-slate-400 font-mono">{{ grids.length }} 个网格</span>
        </div>
        <div class="grid grid-cols-3 gap-4">
          <div
            v-for="grid in grids"
            :key="grid.label"
            class="glass rounded-xl p-4 hover:border-white/20 transition-all cursor-pointer"
          >
            <div class="flex items-center justify-between mb-3">
              <span class="text-lg font-mono font-bold text-white">{{ grid.label }}</span>
              <span
                class="w-3 h-3 rounded-full"
                :class="grid.score >= 0.8 ? 'bg-sakura pulse-red' : grid.score >= 0.5 ? 'bg-amber' : 'bg-cyber-green pulse-green'"
              />
            </div>
            <div class="space-y-1.5 text-xs">
              <div class="flex justify-between">
                <span class="text-slate-500">风险评分</span>
                <span class="font-mono text-white">{{ (grid.score * 100).toFixed(0) }}%</span>
              </div>
              <div class="h-1.5 rounded-full bg-white/5 overflow-hidden">
                <div
                  class="h-full rounded-full transition-all"
                  :class="grid.score >= 0.8 ? 'bg-sakura' : grid.score >= 0.5 ? 'bg-amber' : 'bg-cyber-green'"
                  :style="{ width: `${grid.score * 100}%` }"
                />
              </div>
              <div v-if="grid.pest" class="text-slate-400">
                虫害: <span class="text-white">{{ grid.pest }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Users -->
      <div v-if="activeTab === 'users'" class="flex-1 overflow-y-auto space-y-4">
        <!-- Pending users section -->
        <div v-if="pendingUsers.length > 0">
          <div class="flex items-center gap-2 mb-3">
            <span class="w-2 h-2 rounded-full bg-amber pulse-amber" />
            <span class="text-xs text-amber font-mono">待审核用户 ({{ pendingUsers.length }})</span>
          </div>
          <div class="space-y-2">
            <div
              v-for="user in pendingUsers"
              :key="user.id"
              class="glass rounded-xl p-4 border border-amber/20"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <div class="w-10 h-10 rounded-lg bg-amber/10 flex items-center justify-center text-amber font-mono font-bold">
                    {{ user.username.charAt(0).toUpperCase() }}
                  </div>
                  <div>
                    <div class="text-sm text-white font-medium">{{ user.username }}</div>
                    <div class="text-[10px] text-slate-500 font-mono">{{ user.email || '未填写邮箱' }}</div>
                  </div>
                </div>
                <div class="flex gap-2">
                  <button
                    class="px-3 py-1.5 rounded-lg text-xs bg-cyber-green/10 text-cyber-green border border-cyber-green/20 hover:bg-cyber-green/20 transition-colors"
                    @click="approveAsRole(user.id, 'MANAGER')"
                  >
                    通过为员工
                  </button>
                  <button
                    class="px-3 py-1.5 rounded-lg text-xs bg-blue-400/10 text-blue-400 border border-blue-400/20 hover:bg-blue-400/20 transition-colors"
                    @click="approveAsRole(user.id, 'EXPERT')"
                  >
                    通过为专家
                  </button>
                  <button
                    class="px-3 py-1.5 rounded-lg text-xs bg-orange-400/10 text-orange-400 border border-orange-400/20 hover:bg-orange-400/20 transition-colors"
                    @click="approveAsRole(user.id, 'ADMIN')"
                  >
                    通过为管理员
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Approved users -->
        <div>
          <div class="flex justify-between items-center mb-3">
            <span class="text-xs text-slate-400 font-mono">已注册用户 {{ filteredUsers.length }} 位</span>
            <div class="flex gap-3">
              <input
               v-model="userSearch"
               type="text"
               placeholder="搜索用户名或姓名"
               class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50"
              />
            </div>
          </div>
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-xs text-slate-500 uppercase tracking-wider border-b border-white/5">
                <th class="pb-3 pr-4">用户名</th>
                <th class="pb-3 pr-4">姓名</th>
                <th class="pb-3 pr-4">角色</th>
                <th class="pb-3 pr-4">邮箱</th>
                <th class="pb-3 pr-4">状态</th>
                <th class="pb-3">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in filteredUsers" :key="user.id" class="border-b border-white/5 hover:bg-white/5 transition-colors">
                <td class="py-3 pr-4 text-white font-mono text-xs">{{ user.username }}</td>
                <td class="py-3 pr-4 text-white">{{ user.name }}</td>
                <td class="py-3 pr-4">
                  <span class="text-xs text-slate-400">{{ roleLabels[user.role] || user.role }}</span>
                </td>
                <td class="py-3 pr-4 text-xs text-slate-400 font-mono">{{ user.email || '-' }}</td>
                <td class="py-3 pr-4">
                  <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="user.approved ? 'text-cyber-green bg-cyber-green/10 border-cyber-green/20' : 'text-slate-400 bg-white/5 border-white/10'">
                    {{ user.approved ? '正常' : '已禁用' }}
                  </span>
                </td>
                <td class="py-3">
                  <div v-if="auth.userRole === 'ADMIN'" class="flex gap-2">
                    <button class="text-xs text-cyber-green hover:underline" @click.stop="openEdit(user)">编辑</button>
                    <button class="text-xs text-amber hover:underline" @click.stop="openResetConfirm(user)">重置密码</button>
                    <button
                      class="text-xs hover:underline"
                      :class="user.approved ? 'text-sakura' : 'text-cyber-green'"
                      @click.stop="toggleUserStatus(user)"
                    >
                      {{ user.approved ? '禁用' : '启用' }}
                    </button>
                  </div>
                  <span v-else class="text-xs text-slate-600">-</span>
                </td>
              </tr>
              <tr v-if="filteredUsers.length === 0">
               <td colspan="6" class="py-8 text-center text-slate-500 text-sm">没有找到匹配的用户</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </GlassCard>

    <!-- Edit User Modal -->
    <Teleport to="body">
      <div
        v-if="showEditModal && editingUser"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @click.self="closeEdit"
      >
        <div class="glass rounded-2xl p-6 w-[420px] shadow-2xl border border-white/10">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-lg font-bold text-white">编辑用户</h2>
              <p class="text-xs text-slate-500 font-mono">EDIT USER</p>
            </div>
            <button
              class="w-8 h-8 rounded-lg bg-sakura/10 hover:bg-sakura/20 flex items-center justify-center text-sakura hover:text-white transition-colors"
              @click="closeEdit"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">用户名</label>
              <div class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-slate-500 text-sm font-mono">
                {{ editingUser.username }}
              </div>
              <div class="text-[10px] text-slate-600 mt-1">用户名不可修改</div>
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">姓名</label>
              <input
                v-model="editForm.name"
                type="text"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">角色</label>
              <select
                v-model="editForm.role"
                class="w-full px-4 py-3 rounded-xl bg-slate-800 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50 select-dark"
              >
                <option value="ADMIN">管理员</option>
                <option value="EXPERT">农技专家</option>
                <option value="MANAGER">员工</option>
              </select>
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">手机号</label>
              <input
                v-model="editForm.phone"
                type="text"
                placeholder="请输入手机号"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">邮箱</label>
              <input
                v-model="editForm.email"
                type="email"
                placeholder="请输入邮箱地址"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
              <div v-if="editForm.role === 'EXPERT'" class="text-[10px] text-amber mt-1">专家邮箱必须真实，用于接收系统邮件通知</div>
            </div>
          </div>

          <div class="flex gap-3 mt-6">
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors"
              @click="saveEdit"
            >
              保存
            </button>
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
              @click="closeEdit"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Reset Password Confirm Modal -->
    <Teleport to="body">
      <div
        v-if="showResetConfirm && resettingUser"
        class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm"
        @click.self="closeResetConfirm"
      >
        <div class="glass rounded-2xl p-6 w-[400px] shadow-2xl border border-amber/20">
          <div class="text-center">
            <div class="w-16 h-16 rounded-full bg-amber/10 flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-amber" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
              </svg>
            </div>
            <h3 class="text-lg font-bold text-white mb-2">确认重置密码</h3>
            <p class="text-sm text-slate-400 mb-2">您即将重置以下用户的密码：</p>
            <p class="text-sm font-mono text-white bg-white/5 rounded-lg px-3 py-2 mb-2">{{ resettingUser.username }}</p>
            <p class="text-xs text-slate-500 mb-6">重置后默认密码为 <span class="text-amber font-mono">123456</span>，用户需登录后自行修改</p>
            <div class="flex gap-3">
              <button
                class="flex-1 px-4 py-3 rounded-xl bg-amber/10 border border-amber/20 text-amber text-sm hover:bg-amber/20 transition-colors"
                @click="confirmResetPassword"
              >
                确认重置
              </button>
              <button
                class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
                @click="closeResetConfirm"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Camera Edit Modal -->
    <Teleport to="body">
      <div
        v-if="showCameraModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @click.self="closeCameraModal"
      >
        <div class="glass rounded-2xl p-6 w-[560px] shadow-2xl border border-white/10 max-h-[85vh] overflow-y-auto">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-lg font-bold text-white">编辑摄像头</h2>
              <p class="text-xs text-slate-500 font-mono">EDIT CAMERA</p>
            </div>
            <button
              class="w-8 h-8 rounded-lg bg-sakura/10 hover:bg-sakura/20 flex items-center justify-center text-sakura hover:text-white transition-colors"
              @click="closeCameraModal"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">摄像头名称 <span class="text-sakura">*</span></label>
              <input
                v-model="cameraForm.name"
                type="text"
                placeholder="如：A区-1号摄像头"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>

            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">RTSP 主码流地址 <span class="text-sakura">*</span></label>
              <input
                v-model="cameraForm.rtspUrl"
                type="text"
                placeholder="rtsp://192.168.1.101:554/stream1"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
              <div class="text-[10px] text-slate-600 mt-1">用于抓拍和推理，格式 rtsp://host:port/path</div>
            </div>

            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">RTSP 子码流地址</label>
              <input
                v-model="cameraForm.rtspUrlSub"
                type="text"
                placeholder="rtsp://192.168.1.101:554/stream2（可选）"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
              <div class="text-[10px] text-slate-600 mt-1">低分辨率流，用于前端实时预览以降低带宽</div>
            </div>

            <div class="grid grid-cols-3 gap-4">
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">经度</label>
                <input
                  v-model="cameraForm.locationX"
                  type="text"
                  placeholder="108.9423"
                  class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
                />
              </div>
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">纬度</label>
                <input
                  v-model="cameraForm.locationY"
                  type="text"
                  placeholder="34.2614"
                  class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
                />
              </div>
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">朝向角度</label>
                <input
                  v-model="cameraForm.direction"
                  type="text"
                  placeholder="0-360"
                  class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
                />
              </div>
            </div>

            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">覆盖网格</label>
              <input
                v-model="cameraForm.grid"
                type="text"
                placeholder="A1,A2,A3（逗号分隔）"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>

            <div class="grid grid-cols-3 gap-4">
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">抓拍分辨率</label>
                <select
                  v-model="cameraForm.captureResolution"
                  class="w-full px-4 py-3 rounded-xl bg-slate-800 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50 select-dark"
                >
                  <option value="320x320">320x320</option>
                  <option value="640x640">640x640</option>
                  <option value="1280x1280">1280x1280</option>
                </select>
              </div>
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">JPEG 质量</label>
                <input
                  v-model="cameraForm.captureQuality"
                  type="number"
                  min="1"
                  max="100"
                  class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
                />
              </div>
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">重连间隔(秒)</label>
                <input
                  v-model="cameraForm.reconnectInterval"
                  type="number"
                  min="10"
                  max="300"
                  class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
                />
              </div>
            </div>
          </div>

          <div class="flex gap-3 mt-6">
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              :disabled="!cameraForm.name.trim() || !cameraForm.rtspUrl.trim()"
              @click="saveCamera"
            >
              保存修改
            </button>
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
              @click="closeCameraModal"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.select-dark {
  color-scheme: dark;
}
.select-dark option {
  background-color: #1e293b;
  color: white;
}
</style>

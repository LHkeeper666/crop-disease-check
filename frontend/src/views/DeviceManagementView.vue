<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import { useAuthStore } from '../stores/auth'
import { fetchGrids, updateGrid, type GridVO } from '../api/grid'
import { fetchUsers, updateUser, updateUserStatus, resetUserPassword, type UserSimpleVO } from '../api/user'
import { usePageContextProvider } from '../composables/usePageContext'

const auth = useAuthStore()
const isAdmin = computed(() => auth.userRole === 'ADMIN')

const activeTab = ref<'cameras' | 'grids' | 'users'>('cameras')

// ==================== Camera state ====================
interface CameraItem {
  id: string
  name: string
  rtspUrl: string
  rtspUrlSub?: string
  locationX?: number
  locationY?: number
  direction?: number
  status: string
  coverageGrids?: string[]
  captureResolution?: string
  captureQuality?: number
  reconnectInterval?: number
}

const cameras = ref<CameraItem[]>([])
const cameraLoading = ref(false)
const cameraError = ref('')

// --- 网格 ---
const grids = ref<GridVO[]>([])
const gridLoading = ref(false)

async function loadGrids() {
  gridLoading.value = true
  try {
    grids.value = await fetchGrids()
  } catch (e: any) {
    console.error('[Device] 加载网格失败:', e.message)
  } finally {
    gridLoading.value = false
  }
}

// 网格编辑弹窗
const showGridModal = ref(false)
const editingGrid = ref<GridVO | null>(null)
const gridForm = ref({ label: '', cropType: '', greenhouseId: '' })
const gridSaving = ref(false)

function openEditGrid(grid: GridVO) {
  editingGrid.value = grid
  gridForm.value = {
    label: grid.label,
    cropType: grid.cropType || '',
    greenhouseId: grid.greenhouseId || '',
  }
  showGridModal.value = true
}

async function saveGrid() {
  if (!editingGrid.value || !gridForm.value.label.trim()) return
  gridSaving.value = true
  try {
    await updateGrid(editingGrid.value.id, {
      label: gridForm.value.label,
      cropType: gridForm.value.cropType || undefined,
      greenhouseId: gridForm.value.greenhouseId || undefined,
    })
    showGridModal.value = false
    editingGrid.value = null
    await loadGrids()
  } catch (e: any) {
    alert('保存失败: ' + e.message)
  } finally {
    gridSaving.value = false
  }
}

function closeGridModal() {
  showGridModal.value = false
  editingGrid.value = null
}

// --- 用户 ---
const users = ref<UserSimpleVO[]>([])
const userLoading = ref(false)
const userTotal = ref(0)
const userError = ref('')

async function loadUsers() {
  userLoading.value = true
  userError.value = ''
  try {
    // 传递当前用户的companyId，只显示本公司员工
    const companyId = auth.userInfo?.companyId
    const page = await fetchUsers({ size: 200, companyId: companyId || undefined })
    users.value = page.records
    userTotal.value = page.total
  } catch (e: any) {
    console.error('[Device] 加载用户失败:', e.message)
    userError.value = e.message || '加载用户失败'
  } finally {
    userLoading.value = false
  }
}

const cameraSearch = ref('')
const userSearch = ref('')

const filteredCameras = computed(() => {
  if (!cameraSearch.value) return cameras.value
  const keyword = cameraSearch.value.toLowerCase()
  return cameras.value.filter(cam =>
    cam.name.toLowerCase().includes(keyword)
  )
})

usePageContextProvider(() => ({
  page: '/devices',
  pageName: '设备管理',
  visibleData: {
    list: cameras.value.slice(0, 5).map(cam => ({
      id: cam.id,
      name: cam.name,
      status: cam.status,
      coverageGrids: cam.coverageGrids,
    })),
    stats: {
      totalCameras: cameras.value.length,
      onlineCameras: cameras.value.filter(c => c.status === 'ONLINE').length,
      totalGrids: grids.value.length,
      totalUsers: users.value.length,
    },
    extra: {
      grids: grids.value.slice(0, 5).map(g => ({
        id: g.id,
        label: g.label,
        cropType: g.cropType,
      })),
    },
  },
}))

// ==================== Camera API ====================
function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token')
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  }
}

async function fetchCameras() {
  cameraLoading.value = true
  cameraError.value = ''
  try {
    const res = await fetch(`/api/camera/list?size=100&_t=${Date.now()}`, {
      headers: getAuthHeaders(),
    })
    if (!res.ok) throw new Error('加载摄像头列表失败')
    const data = await res.json()
    if (data.code !== 200) throw new Error(data.message || '加载失败')
    const records = data.data?.records || []
    console.log('[Camera] 列表加载, 共', records.length, '条')
    cameras.value = records.map((c: any) => ({
      id: c.id,
      name: c.name,
      rtspUrl: c.rtspUrl || '',
      rtspUrlSub: c.rtspUrlSub || '',
      locationX: c.locationX,
      locationY: c.locationY,
      direction: c.direction,
      status: 'OFFLINE', // 默认离线，由探测确定真实状态
      coverageGrids: c.coverageGrids || [],
      captureResolution: c.captureResolution || '',
      captureQuality: c.captureQuality ?? 85,
      reconnectInterval: c.reconnectInterval ?? 30,
    }))
  } catch (e: any) {
    cameraError.value = e.message || '加载摄像头失败'
  } finally {
    cameraLoading.value = false
  }
}

const cameraProbing = ref(false)

/**
 * 从前端主动探测摄像头网络可达性
 * 通过 HTTP fetch 超时判断 IP:port 是否可达
 */
async function probeCameraReachability(cam: CameraItem): Promise<string> {
  if (!cam.rtspUrl) return 'OFFLINE'
  try {
    const url = new URL(cam.rtspUrl)
    const probeUrl = `http://${url.hostname}:${url.port || 554}/`
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 3000)
    await fetch(probeUrl, { mode: 'no-cors', signal: controller.signal })
    clearTimeout(timer)
    return 'ONLINE'
  } catch {
    return 'OFFLINE'
  }
}

async function probeAllCameras() {
  cameraProbing.value = true
  try {
    await Promise.allSettled(
      cameras.value.map(async (cam) => {
        cam.status = await probeCameraReachability(cam)
      })
    )
  } finally {
    cameraProbing.value = false
  }
}

async function createCamera(form: typeof cameraForm.value) {
  const res = await fetch('/api/camera', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({
      name: form.name.trim(),
      rtspUrl: form.rtspUrl.trim(),
      rtspUrlSub: form.rtspUrlSub.trim() || undefined,
      locationX: form.locationX ? parseFloat(form.locationX) : undefined,
      locationY: form.locationY ? parseFloat(form.locationY) : undefined,
      direction: form.direction ? parseFloat(form.direction) : undefined,
      coverageGrids: form.grid
        ? form.grid.split(',').map((s: string) => s.trim()).filter(Boolean)
        : undefined,
      captureResolution: form.captureResolution || undefined,
      captureQuality: parseInt(form.captureQuality) || 85,
      reconnectInterval: parseInt(form.reconnectInterval) || 30,
    }),
  })
  const data = await res.json()
  if (data.code !== 200) throw new Error(data.message || '新增失败')
}

async function updateCamera(id: string, form: typeof cameraForm.value) {
  const body = {
    name: form.name.trim(),
    rtspUrl: form.rtspUrl.trim(),
    rtspUrlSub: form.rtspUrlSub.trim() || undefined,
    locationX: form.locationX ? parseFloat(form.locationX) : undefined,
    locationY: form.locationY ? parseFloat(form.locationY) : undefined,
    direction: form.direction ? parseFloat(form.direction) : undefined,
    coverageGrids: form.grid
      ? form.grid.split(',').map((s: string) => s.trim()).filter(Boolean)
      : undefined,
    captureResolution: form.captureResolution || undefined,
    captureQuality: parseInt(form.captureQuality) || 85,
    reconnectInterval: parseInt(form.reconnectInterval) || 30,
  }
  console.log('[Camera] 更新请求:', `/api/camera/${id}`, body)
  const res = await fetch(`/api/camera/${id}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(body),
  })
  const data = await res.json()
  console.log('[Camera] 更新响应:', data)
  if (data.code !== 200) throw new Error(data.message || '更新失败')
}

async function deleteCamera(id: string) {
  const res = await fetch(`/api/camera/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  })
  const data = await res.json()
  if (data.code !== 200) throw new Error(data.message || '删除失败')
}

// ==================== Camera modal ====================
const showCameraModal = ref(false)
const editingCamera = ref<CameraItem | null>(null)
const isCreateMode = ref(false)
const cameraForm = ref({
  name: '', rtspUrl: '', rtspUrlSub: '',
  locationX: '', locationY: '', direction: '0',
  grid: '', captureResolution: '',
  captureQuality: '85', reconnectInterval: '30',
})
const cameraSaving = ref(false)
const cameraSaveError = ref('')

function openCreateCamera() {
  isCreateMode.value = true
  editingCamera.value = null
  cameraForm.value = {
    name: '', rtspUrl: '', rtspUrlSub: '',
    locationX: '', locationY: '', direction: '0',
    grid: '', captureResolution: '',
    captureQuality: '85', reconnectInterval: '30',
  }
  cameraSaveError.value = ''
  showCameraModal.value = true
}

function openEditCamera(cam: CameraItem) {
  isCreateMode.value = false
  editingCamera.value = cam
  cameraForm.value = {
    name: cam.name,
    rtspUrl: cam.rtspUrl,
    rtspUrlSub: cam.rtspUrlSub || '',
    locationX: String(cam.locationX ?? ''),
    locationY: String(cam.locationY ?? ''),
    direction: String(cam.direction ?? 0),
    grid: cam.coverageGrids?.join(', ') || '',
    captureResolution: cam.captureResolution || '',
    captureQuality: String(cam.captureQuality ?? 85),
    reconnectInterval: String(cam.reconnectInterval ?? 30),
  }
  cameraSaveError.value = ''
  showCameraModal.value = true
}

async function saveCamera() {
  if (!cameraForm.value.name.trim() || !cameraForm.value.rtspUrl.trim()) return
  cameraSaving.value = true
  cameraSaveError.value = ''
  try {
    if (isCreateMode.value) {
      await createCamera(cameraForm.value)
    } else {
      const camId = editingCamera.value?.id
      if (!camId) throw new Error('摄像头ID无效')
      console.log('[Camera] 开始更新, id=', camId)
      await updateCamera(camId, cameraForm.value)
      console.log('[Camera] 更新成功')
    }
    showCameraModal.value = false
    editingCamera.value = null
    await fetchCameras()
    console.log('[Camera] 列表已刷新')
  } catch (e: any) {
    console.error('[Camera] 保存失败:', e)
    cameraSaveError.value = e.message || '操作失败'
  } finally {
    cameraSaving.value = false
  }
}

function closeCameraModal() {
  showCameraModal.value = false
  editingCamera.value = null
}

// Delete confirm
const showDeleteConfirm = ref(false)
const deletingCamera = ref<CameraItem | null>(null)
const deleteLoading = ref(false)
const deleteError = ref('')

function openDeleteConfirm(cam: CameraItem) {
  deletingCamera.value = cam
  deleteError.value = ''
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (!deletingCamera.value) return
  deleteLoading.value = true
  deleteError.value = ''
  try {
    await deleteCamera(deletingCamera.value.id)
    showDeleteConfirm.value = false
    deletingCamera.value = null
    await fetchCameras()
  } catch (e: any) {
    deleteError.value = e.message || '删除失败'
  } finally {
    deleteLoading.value = false
  }
}

function closeDeleteConfirm() {
  showDeleteConfirm.value = false
  deletingCamera.value = null
}

onMounted(async () => {
  await fetchCameras()
  probeAllCameras() // 获取列表后立即探测摄像头真实状态
  loadGrids()
  loadUsers()
})

// 切换到用户管理 tab 时，若用户列表为空且未在加载中，自动重新加载
watch(activeTab, (tab) => {
  if (tab === 'users' && users.value.length === 0 && !userLoading.value) {
    loadUsers()
  }
})

// ==================== User management ====================
const filteredUsers = computed(() => {
  if (!userSearch.value) return users.value
  const keyword = userSearch.value.toLowerCase()
  return users.value.filter(user =>
    user.username.toLowerCase().includes(keyword) ||
    (user.name || '').toLowerCase().includes(keyword)
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
  MANAGER: '操作员',
  STAFF: '基层员工',
}

// Edit user modal
const showEditModal = ref(false)
const editingUser = ref<UserSimpleVO | null>(null)
const editForm = ref({ name: '', role: '', phone: '', email: '' })
const editSaving = ref(false)
const editError = ref('')

function openEdit(user: UserSimpleVO) {
  editingUser.value = user
  editForm.value = { name: user.name || '', role: user.role, phone: user.phone || '', email: user.email || '' }
  editError.value = ''
  showEditModal.value = true
}

async function saveEdit() {
  if (!editingUser.value) return
  editSaving.value = true
  editError.value = ''
  try {
    await updateUser(editingUser.value.id, {
      name: editForm.value.name,
      role: editForm.value.role,
      phone: editForm.value.phone || undefined,
      email: editForm.value.email || undefined,
    })
    showEditModal.value = false
    editingUser.value = null
    await loadUsers()
  } catch (e: any) {
    editError.value = e.message || '保存失败'
  } finally {
    editSaving.value = false
  }
}

function closeEdit() {
  showEditModal.value = false
  editingUser.value = null
}

// Reset password
const showResetConfirm = ref(false)
const resettingUser = ref<UserSimpleVO | null>(null)
const resetNewPassword = ref('')

async function confirmResetPassword() {
  if (!resettingUser.value) return
  try {
    // 生成随机密码（字母+数字，8位）
    const chars = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
    let newPwd = ''
    for (let i = 0; i < 8; i++) newPwd += chars.charAt(Math.floor(Math.random() * chars.length))
    await resetUserPassword(resettingUser.value.id, newPwd)
    resetNewPassword.value = newPwd
  } catch (e: any) {
    alert('重置失败: ' + e.message)
    closeResetConfirm()
  }
}

function closeResetConfirm() {
  showResetConfirm.value = false
  resettingUser.value = null
  resetNewPassword.value = ''
}

function openResetConfirm(user: UserSimpleVO) {
  resettingUser.value = user
  resetNewPassword.value = ''
  showResetConfirm.value = true
}

// Toggle disable/enable
async function toggleUserStatus(user: UserSimpleVO) {
  const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await updateUserStatus(user.id, newStatus)
    await loadUsers()
  } catch (e: any) {
    alert('操作失败: ' + e.message)
  }
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">多用户生物资产与设备管理舱</h1>
        <p class="text-xs text-slate-500 font-mono">多用户生物资产与设备管理</p>
      </div>
    </div>

    <!-- Tab bar -->
    <div class="flex gap-2 shrink-0">
      <button
        v-for="tab in [
          { key: 'cameras', label: '摄像头管理' },
          { key: 'grids', label: '网格区域' },
          ...(isAdmin ? [{ key: 'users', label: '用户管理' }] : []),
        ].filter(Boolean)"
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
        <!-- Error -->
        <div v-if="cameraError" class="mb-4 px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs">
          {{ cameraError }}
          <button class="ml-2 underline" @click="fetchCameras">重试</button>
        </div>

        <!-- Loading -->
        <div v-if="cameraLoading" class="flex items-center justify-center py-16">
          <div class="text-slate-500 text-sm">加载中...</div>
        </div>

        <template v-else>
          <div class="flex justify-between items-center mb-4">
            <span class="text-xs text-slate-400 font-mono">{{ filteredCameras.length }} 台设备</span>
            <div class="flex gap-3">
              <input
                v-model="cameraSearch"
                type="text"
                placeholder="搜索摄像头名称或网格"
                class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50"
              />
              <button
                class="px-3 py-1.5 rounded-lg text-xs font-mono bg-[#FF6A00]/10 border border-[#FF6A00]/20 text-[#FF6A00] hover:bg-[#FF6A00]/20 transition-colors"
                @click="openCreateCamera"
              >
                + 新增摄像头
              </button>
              <button
                class="px-3 py-1.5 rounded-lg text-xs font-mono bg-white/5 border border-white/10 text-slate-400 hover:text-white hover:bg-white/10 transition-colors disabled:opacity-50"
                :disabled="cameraProbing"
                @click="probeAllCameras"
              >
                {{ cameraProbing ? '探测中...' : '探测状态' }}
              </button>
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
                <td class="py-3 pr-4 text-slate-400 font-mono text-xs">{{ cam.coverageGrids?.join(', ') || '-' }}</td>
                <td class="py-3 pr-4 text-slate-500 font-mono text-xs truncate max-w-48">{{ cam.rtspUrl }}</td>
                <td class="py-3">
                  <div class="flex gap-2">
                    <button class="text-xs text-cyber-green hover:underline" @click.stop="openEditCamera(cam)">编辑</button>
                    <button class="text-xs text-sakura hover:underline" @click.stop="openDeleteConfirm(cam)">删除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="filteredCameras.length === 0">
                <td colspan="5" class="py-8 text-center text-slate-500 text-sm">
                  {{ cameraSearch ? '没有找到匹配的摄像头' : '暂无摄像头设备，请点击上方按钮新增' }}
                </td>
              </tr>
            </tbody>
          </table>
        </template>
      </div>

      <!-- Grids -->
      <div v-if="activeTab === 'grids'" class="flex-1 overflow-y-auto">
        <div class="flex justify-between items-center mb-4">
          <span class="text-xs text-slate-400 font-mono">网格区域管理</span>
        </div>
        <div v-if="gridLoading" class="py-8 text-center text-slate-500 text-sm">加载中...</div>
        <div v-else class="grid grid-cols-2 lg:grid-cols-3 gap-4">
          <div
            v-for="grid in grids"
            :key="grid.id"
            class="glass rounded-xl p-4 transition-all group"
            :class="isAdmin ? 'hover:border-cyber-green/30 cursor-pointer' : 'cursor-default'"
            @click="isAdmin && openEditGrid(grid)"
          >
            <div class="flex items-center justify-between mb-3">
              <span class="text-lg font-mono font-bold text-white">{{ grid.label }}</span>
              <div class="flex items-center gap-2">
                <span class="w-3 h-3 rounded-full bg-cyber-green pulse-green" />
                <svg v-if="isAdmin" class="w-3.5 h-3.5 text-slate-600 group-hover:text-cyber-green transition-colors" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" />
                </svg>
              </div>
            </div>
            <div class="space-y-1.5 text-xs">
              <div class="flex justify-between">
                <span class="text-slate-500">作物类型</span>
                <span class="font-mono text-white">{{ grid.cropType || '-' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-slate-500">面积</span>
                <span class="font-mono text-white">{{ grid.areaM2 ? grid.areaM2 + ' m²' : '-' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Users -->
      <div v-if="activeTab === 'users'" class="flex-1 overflow-y-auto space-y-4">
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
              <button
                class="px-3 py-1.5 rounded-lg text-xs font-mono bg-white/5 text-slate-400 border border-white/10 hover:bg-white/10 transition-colors"
                @click="loadUsers"
              >
                刷新
              </button>
            </div>
          </div>
          <!-- Error state -->
          <div v-if="userError" class="mb-4 px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs flex items-center gap-2">
            {{ userError }}
            <button class="ml-2 underline text-cyber-green" @click="loadUsers">重试</button>
          </div>
          <div v-if="userLoading" class="py-8 text-center text-slate-500 text-sm">加载中...</div>
          <table v-else class="w-full text-sm">
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
                  <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="user.status === 'ACTIVE' ? 'text-cyber-green bg-cyber-green/10 border-cyber-green/20' : 'text-slate-400 bg-white/5 border-white/10'">
                    {{ user.status === 'ACTIVE' ? '正常' : '已禁用' }}
                  </span>
                </td>
                <td class="py-3">
                  <div v-if="auth.userRole === 'ADMIN'" class="flex gap-2">
                    <button class="text-xs text-cyber-green hover:underline" @click.stop="openEdit(user)">编辑</button>
                    <button class="text-xs text-amber hover:underline" @click.stop="openResetConfirm(user)">重置密码</button>
                    <button
                      class="text-xs hover:underline"
                      :class="user.status === 'ACTIVE' ? 'text-sakura' : 'text-cyber-green'"
                      @click.stop="toggleUserStatus(user)"
                    >
                      {{ user.status === 'ACTIVE' ? '禁用' : '启用' }}
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
        @mousedown.self="closeEdit"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[420px] mx-4 shadow-2xl border border-white/10">
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
                <option value="MANAGER">操作员</option>
                <option value="STAFF">基层员工</option>
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

          <div v-if="editError" class="mt-3 px-3 py-2 rounded-lg bg-sakura/10 border border-sakura/20 text-sakura text-xs">
            {{ editError }}
          </div>

          <div class="flex gap-3 mt-4">
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors disabled:opacity-40"
              :disabled="editSaving"
              @click="saveEdit"
            >
              {{ editSaving ? '保存中...' : '保存' }}
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
        @mousedown.self="closeResetConfirm"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[400px] mx-4 shadow-2xl border border-amber/20">
          <div class="text-center">
            <div class="w-16 h-16 rounded-full bg-amber/10 flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-amber" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
              </svg>
            </div>
            <template v-if="!resetNewPassword">
              <h3 class="text-lg font-bold text-white mb-2">确认重置密码</h3>
              <p class="text-sm text-slate-400 mb-2">您即将重置以下用户的密码：</p>
              <p class="text-sm font-mono text-white bg-white/5 rounded-lg px-3 py-2 mb-6">{{ resettingUser.username }}</p>
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
            </template>
            <template v-else>
              <h3 class="text-lg font-bold text-white mb-2">重置成功</h3>
              <p class="text-sm text-slate-400 mb-2">新密码为：</p>
              <p class="text-lg font-mono text-amber bg-amber/10 rounded-lg px-4 py-3 mb-4 select-all">{{ resetNewPassword }}</p>
              <p class="text-xs text-slate-500 mb-6">请记录此密码，关闭后无法再次查看</p>
              <button
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
                @click="closeResetConfirm"
              >
                关闭
              </button>
            </template>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Grid Edit Modal -->
    <Teleport to="body">
      <div
        v-if="showGridModal && editingGrid"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @mousedown.self="closeGridModal"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[420px] mx-4 shadow-2xl border border-white/10">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-lg font-bold text-white">编辑网格</h2>
              <p class="text-xs text-slate-500 font-mono">EDIT GRID</p>
            </div>
            <button
              class="w-8 h-8 rounded-lg bg-sakura/10 hover:bg-sakura/20 flex items-center justify-center text-sakura hover:text-white transition-colors"
              @click="closeGridModal"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">网格编号 <span class="text-sakura">*</span></label>
              <input
                v-model="gridForm.label"
                type="text"
                placeholder="如 A1、B3"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">作物类型</label>
              <input
                v-model="gridForm.cropType"
                type="text"
                placeholder="如 番茄、黄瓜"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">关联大棚 ID</label>
              <input
                v-model="gridForm.greenhouseId"
                type="text"
                placeholder="可选"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>
            <div class="flex justify-between items-center py-2 px-1 text-xs text-slate-500">
              <span>网格 ID: {{ editingGrid.id }}</span>
              <span v-if="editingGrid.areaM2">面积: {{ editingGrid.areaM2 }} m²</span>
            </div>
          </div>

          <div class="flex gap-3 mt-6">
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors disabled:opacity-40"
              :disabled="!gridForm.label.trim() || gridSaving"
              @click="saveGrid"
            >
              {{ gridSaving ? '保存中...' : '保存' }}
            </button>
            <button
              class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
              @click="closeGridModal"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Camera Create/Edit Modal -->
    <Teleport to="body">
      <div
        v-if="showCameraModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @mousedown.self="closeCameraModal"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[560px] mx-4 shadow-2xl border border-white/10 max-h-[85vh] overflow-y-auto">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-lg font-bold text-white">{{ isCreateMode ? '新增摄像头' : '编辑摄像头' }}</h2>
              <p class="text-xs text-slate-500 font-mono">{{ isCreateMode ? 'CREATE CAMERA' : 'EDIT CAMERA' }}</p>
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

          <!-- Error -->
          <div v-if="cameraSaveError" class="mb-4 px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs">
            {{ cameraSaveError }}
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
            </div>

            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">RTSP 子码流地址</label>
              <input
                v-model="cameraForm.rtspUrlSub"
                type="text"
                placeholder="rtsp://192.168.1.101:554/stream2（可选）"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>

            <div class="grid grid-cols-2 lg:grid-cols-3 gap-4">
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
                placeholder="g001,g002（逗号分隔）"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
            </div>

            <div class="grid grid-cols-2 lg:grid-cols-3 gap-4">
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">抓拍分辨率</label>
                <select
                  v-model="cameraForm.captureResolution"
                  class="w-full px-4 py-3 rounded-xl bg-slate-800 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50 select-dark"
                >
                  <option value="">源流分辨率（自适应）</option>
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
              :disabled="!cameraForm.name.trim() || !cameraForm.rtspUrl.trim() || cameraSaving"
              @click="saveCamera"
            >
              {{ cameraSaving ? '保存中...' : (isCreateMode ? '确认新增' : '保存修改') }}
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

    <!-- Delete Confirm Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm && deletingCamera"
        class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm"
        @mousedown.self="closeDeleteConfirm"
      >
        <div class="glass rounded-2xl p-6 w-[400px] shadow-2xl border border-sakura/20">
          <div class="text-center">
            <div class="w-16 h-16 rounded-full bg-sakura/10 flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-sakura" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
              </svg>
            </div>
            <h3 class="text-lg font-bold text-white mb-2">确认删除摄像头</h3>
            <p class="text-sm text-slate-400 mb-2">您即将删除以下摄像头：</p>
            <p class="text-sm font-mono text-white bg-white/5 rounded-lg px-3 py-2 mb-2">{{ deletingCamera.name }}</p>
            <p class="text-xs text-slate-500 mb-6">删除后将断开RTSP连接并清理关联资源，此操作不可撤销</p>

            <!-- Error -->
            <div v-if="deleteError" class="mb-4 px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs text-left">
              {{ deleteError }}
            </div>

            <div class="flex gap-3">
              <button
                class="flex-1 px-4 py-3 rounded-xl bg-sakura/10 border border-sakura/20 text-sakura text-sm hover:bg-sakura/20 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                :disabled="deleteLoading"
                @click="confirmDelete"
              >
                {{ deleteLoading ? '删除中...' : '确认删除' }}
              </button>
              <button
                class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
                @click="closeDeleteConfirm"
              >
                取消
              </button>
            </div>
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

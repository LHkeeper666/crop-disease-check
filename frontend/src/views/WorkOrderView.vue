<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import GlowButton from '../components/GlowButton.vue'
import { useWorkOrderStore } from '../stores/workorder'
import { useAuthStore } from '../stores/auth'
import { fetchExperts, type UserSimpleVO } from '../api/user'

const woStore = useWorkOrderStore()
const authStore = useAuthStore()

// 是否为专家角色
const isExpert = computed(() => authStore.userRole === 'EXPERT')

// 专家列表
const experts = ref<UserSimpleVO[]>([])
const expertsLoading = ref(false)

// 加载专家列表
async function loadExperts() {
  expertsLoading.value = true
  try {
    experts.value = await fetchExperts()
  } catch (e: any) {
    console.error('[WorkOrderView] 加载专家列表失败:', e.message)
  } finally {
    expertsLoading.value = false
  }
}

// 页面加载时从后端拉取工单数据和专家列表
onMounted(() => {
  woStore.fetchOrders()
  loadExperts()
  // 添加点击外部关闭选择器的事件监听
  document.addEventListener('click', handleClickOutside)
})

// 组件卸载时清理事件监听
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

const filterStatus = ref<string>('ALL')
const filterSeverity = ref<string>('ALL')
const filterDateStart = ref('')
const filterDateEnd = ref('')

// Modal states
const showCreateModal = ref(false)
const showDetailModal = ref(false)
const showDeleteConfirm = ref(false)
const showImageModal = ref(false)
const selectedOrder = ref<any>(null)

// 专家选择器状态
const showAssigneeSelector = ref(false)
const expertSearchQuery = ref('')
const assigneeFieldRef = ref<HTMLElement | null>(null)
const assigneeDropdownStyle = ref({})

// New order form
const newOrder = ref({
  gridLabel: '',
  pestName: '',
  type: 'disease' as 'disease' | 'pest',
  severity: 'MEDIUM',
  assignedTo: '',
  confidence: 0.8,
})

// 表单验证错误
const formErrors = ref<Record<string, string>>({})

const gridOptions = ['A1', 'A2', 'A3', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3']

const severityConfig: Record<string, { label: string; color: string; glow: string; level: number }> = {
  CRITICAL: { label: '紧急', color: 'text-sakura bg-sakura/10 border-sakura/20', glow: 'glow-red', level: 4 },
  HIGH: { label: '高危', color: 'text-orange-400 bg-orange-400/10 border-orange-400/20', glow: 'glow-amber', level: 3 },
  MEDIUM: { label: '中等', color: 'text-amber bg-amber/10 border-amber/20', glow: '', level: 2 },
  LOW: { label: '低', color: 'text-slate-400 bg-white/5 border-white/10', glow: '', level: 1 },
}

const statusConfig: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'text-amber bg-amber/10 border-amber/20' },
  PROCESSING: { label: '处理中', color: 'text-blue-400 bg-blue-400/10 border-blue-400/20' },
  DONE: { label: '已完成', color: 'text-cyber-green bg-cyber-green/10 border-cyber-green/20' },
  IGNORED: { label: '已忽略', color: 'text-slate-500 bg-white/5 border-white/10' },
}

/** 获取状态配置，未知状态回退到待处理样式 */
function getStatusConf(status: string) {
  return statusConfig[status] || { label: status || '未知', color: 'text-amber bg-amber/10 border-amber/20' }
}

const filteredOrders = computed(() => {
  return woStore.orders.filter(o => {
    if (filterStatus.value !== 'ALL' && o.status !== filterStatus.value) return false
    if (filterSeverity.value !== 'ALL' && o.severity !== filterSeverity.value) return false
    if (filterDateStart.value) {
      const orderDate = o.createdAt.slice(0, 10)
      if (orderDate < filterDateStart.value) return false
    }
    if (filterDateEnd.value) {
      const orderDate = o.createdAt.slice(0, 10)
      if (orderDate > filterDateEnd.value) return false
    }
    return true
  })
})

const stats = computed(() => ({
  total: woStore.orders.length,
  pending: woStore.orders.filter(o => o.status === 'PENDING').length,
  processing: woStore.orders.filter(o => o.status === 'PROCESSING').length,
  done: woStore.orders.filter(o => o.status === 'DONE').length,
}))

function getStatusSteps(order: any) {
  const steps = [
    { key: 'PENDING', label: '待处理', color: 'bg-amber' },
    { key: 'PROCESSING', label: '处理中', color: 'bg-blue-400' },
    { key: 'DONE', label: '已完成', color: 'bg-cyber-green' },
  ]

  const statusOrder = ['PENDING', 'PROCESSING', 'DONE']
  const currentIdx = statusOrder.indexOf(order.status)

  return steps.map((step, idx) => ({
    ...step,
    active: idx <= currentIdx && order.status !== 'IGNORED',
    ignored: order.status === 'IGNORED',
  }))
}

function openDetail(order: any) {
  selectedOrder.value = order
  showDetailModal.value = true
  // 加载专家列表（如果尚未加载）
  if (woStore.experts.length === 0) {
    woStore.fetchExperts()
  }
}

// 专家列表过滤
const filteredExperts = computed(() => {
  if (!expertSearchQuery.value) return woStore.experts
  const query = expertSearchQuery.value.toLowerCase()
  return woStore.experts.filter(expert =>
    expert.name.toLowerCase().includes(query) ||
    expert.email.toLowerCase().includes(query)
  )
})

function closeDetail() {
  showDetailModal.value = false
  selectedOrder.value = null
  showAssigneeSelector.value = false
  expertSearchQuery.value = ''
}

function selectExpert(expert: any) {
  if (selectedOrder.value) {
    selectedOrder.value.assignedToName = expert.name
    selectedOrder.value.assignedToId = expert.id
  }
  showAssigneeSelector.value = false
  expertSearchQuery.value = ''
}

function toggleAssigneeSelector() {
  showAssigneeSelector.value = !showAssigneeSelector.value
  if (showAssigneeSelector.value) {
    // 计算下拉框位置
    nextTick(() => {
      if (assigneeFieldRef.value) {
        const rect = assigneeFieldRef.value.getBoundingClientRect()
        assigneeDropdownStyle.value = {
          position: 'fixed',
          top: `${rect.bottom + 4}px`,
          left: `${rect.left}px`,
          width: `${rect.width}px`,
          zIndex: 9999,
        }
      }
    })
  } else {
    expertSearchQuery.value = ''
  }
}

// 点击外部关闭选择器
function handleClickOutside(event: Event) {
  const target = event.target as HTMLElement
  if (!target.closest('.assignee-selector') && !target.closest('.assignee-dropdown')) {
    showAssigneeSelector.value = false
    expertSearchQuery.value = ''
  }
}

// 确认修改（保存指派专家）
async function confirmUpdate() {
  if (!selectedOrder.value || !selectedOrder.value.assignedToId) return
  try {
    // 传递专家用户ID
    await woStore.updateAssignee(selectedOrder.value.id, selectedOrder.value.assignedToId)
    // 刷新工单列表以获取最新的 assignedToName
    await woStore.fetchOrders()
    // 更新选中的工单数据
    selectedOrder.value = woStore.orders.find(o => o.id === selectedOrder.value.id)
  } catch {
    // 错误已在 store 中处理
  }
}

function openCreateModal() {
  newOrder.value = {
    gridLabel: '',
    pestName: '',
    type: 'disease',
    severity: 'MEDIUM',
    assignedTo: '',
    confidence: 0.8,
  }
  formErrors.value = {}
  showCreateModal.value = true
}

function closeCreateModal() {
  showCreateModal.value = false
}

async function createOrder() {
  // 表单验证
  formErrors.value = {}

  if (!newOrder.value.gridLabel) {
    formErrors.value.gridLabel = '请选择网格区域'
  }
  if (!newOrder.value.pestName) {
    formErrors.value.pestName = '请输入病虫害名称'
  }

  // 如果有错误，停止提交
  if (Object.keys(formErrors.value).length > 0) {
    return
  }

  // 检查是否已存在同网格+同病虫害的未完成工单
  const duplicateOrder = woStore.orders.find(o =>
    o.gridLabel === newOrder.value.gridLabel &&
    o.pestName === newOrder.value.pestName &&
    (o.status === 'PENDING' || o.status === 'PROCESSING')
  )

  if (duplicateOrder) {
    formErrors.value.pestName = `该网格已存在"${newOrder.value.pestName}"的未完成工单（#${duplicateOrder.id}）`
    return
  }

  const severityPrefix: Record<string, string> = {
    CRITICAL: '【紧急】',
    HIGH: '【高危】',
    MEDIUM: '【中等】',
    LOW: '【低】',
  }

  try {
    await woStore.addOrder({
      title: `${severityPrefix[newOrder.value.severity]}Grid-${newOrder.value.gridLabel} ${newOrder.value.pestName || '异常检测'}`,
      severity: newOrder.value.severity,
      type: newOrder.value.type,
      gridLabel: newOrder.value.gridLabel,
      pestName: newOrder.value.pestName,
      confidence: newOrder.value.confidence,
      assignedTo: newOrder.value.assignedTo || undefined,
    })
    closeCreateModal()
  } catch {
    // 错误已在 store 中处理
  }
}

async function updateOrderStatus(orderId: number, newStatus: string) {
  try {
    await woStore.updateOrderStatus(orderId, newStatus as any)
    // 更新选中的工单
    if (selectedOrder.value?.id === orderId) {
      selectedOrder.value = woStore.orders.find(o => o.id === orderId)
    }
  } catch {
    // 错误已在 store 中处理
  }
}

function openDeleteConfirm(order: any) {
  selectedOrder.value = order
  showDeleteConfirm.value = true
}

function closeDeleteConfirm() {
  showDeleteConfirm.value = false
}

async function deleteOrder() {
  if (!selectedOrder.value) return
  try {
    await woStore.removeOrder(selectedOrder.value.id)
    closeDeleteConfirm()
    closeDetail()
  } catch {
    // 错误已在 store 中处理
  }
}

function sendEmailToExpert() {
  if (!selectedOrder.value) return
  emailPreviewLoading.value = true
  emailPreviewData.value = null
  emailContent.value = ''
  emailSending.value = false
  emailResult.value = ''
  emailError.value = ''
  showEmailModal.value = true
  loadEmailPreview()
}

// ==================== 发送邮件 ====================
const showEmailModal = ref(false)
const emailPreviewLoading = ref(false)
const emailPreviewData = ref<{ toUserId: string; toName: string; toEmail: string; subject: string } | null>(null)
const emailContent = ref('')
const emailSending = ref(false)
const emailResult = ref('')
const emailError = ref('')

async function loadEmailPreview() {
  if (!selectedOrder.value) return
  emailPreviewLoading.value = true
  emailError.value = ''
  try {
    const { previewWorkOrderEmail } = await import('../api/workorder')
    const preview = await previewWorkOrderEmail(selectedOrder.value.id)
    emailPreviewData.value = {
      toUserId: preview.toUserId,
      toName: preview.toName,
      toEmail: preview.toEmail,
      subject: preview.subject,
    }
    emailContent.value = preview.content
  } catch (e: any) {
    emailError.value = e.message || '加载邮件预览失败'
  } finally {
    emailPreviewLoading.value = false
  }
}

async function sendEmail() {
  if (!selectedOrder.value || !emailPreviewData.value) return
  emailSending.value = true
  emailResult.value = ''
  try {
    const token = localStorage.getItem('treeforge_token') || ''
    let toUserId = emailPreviewData.value.toUserId

    // 兜底：如果预览接口没返回 toUserId（后端未更新），通过姓名+邮箱查找
    if (!toUserId) {
      const expertsRes = await fetch(`/api/users?role=EXPERT&size=100`, {
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      })
      const expertsData = await expertsRes.json()
      if (expertsData.code === 200 && expertsData.data?.records) {
        const match = expertsData.data.records.find((u: any) =>
          u.name === emailPreviewData.value!.toName && u.email === emailPreviewData.value!.toEmail
        )
        if (match) toUserId = match.id
      }
    }

    if (!toUserId) {
      emailResult.value = '未找到对应的专家用户'
      return
    }

    const res = await fetch(`/api/workorder/${selectedOrder.value.id}/send-email`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        toUserId,
        subject: emailPreviewData.value.subject,
        content: emailContent.value,
      }),
    })
    const data = await res.json()
    if (data.code === 200) {
      emailResult.value = 'success'
    } else {
      emailResult.value = data.message || '发送失败'
    }
  } catch (e: any) {
    emailResult.value = e.message || '发送失败'
  } finally {
    emailSending.value = false
  }
}

function closeEmailModal() {
  showEmailModal.value = false
  emailResult.value = ''
  emailError.value = ''
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">自动事件响应与智能工单流转舱</h1>
        <p class="text-xs text-slate-500 font-mono">EVENT-DRIVEN WORKORDER MANAGEMENT</p>
      </div>
      <GlowButton v-if="!isExpert" label="+ 手动创建工单" @click="openCreateModal" />
    </div>

    <!-- 专家视角提示 -->
    <div v-if="isExpert" class="shrink-0 glass rounded-xl px-4 py-2.5 flex items-center gap-3 border border-cyber-green/20">
      <div class="w-8 h-8 rounded-lg bg-cyber-green/10 flex items-center justify-center">
        <svg class="w-4 h-4 text-cyber-green" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
      </div>
      <div>
        <p class="text-xs text-cyber-green font-medium">专家视图</p>
        <p class="text-[10px] text-slate-500">当前仅显示指派给您的工单，您可在此处理和更新工单状态</p>
      </div>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 shrink-0">
      <div class="glass rounded-xl px-4 py-3 flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center text-lg font-mono font-bold text-white">{{ stats.total }}</div>
        <div>
          <div class="text-[10px] text-slate-500">全部</div>
          <div class="text-xs text-white font-mono">全部工单</div>
        </div>
      </div>
      <div class="glass rounded-xl px-4 py-3 flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-amber/10 flex items-center justify-center text-lg font-mono font-bold text-amber">{{ stats.pending }}</div>
        <div>
          <div class="text-[10px] text-slate-500">待处理</div>
          <div class="text-xs text-white font-mono">待处理</div>
        </div>
      </div>
      <div class="glass rounded-xl px-4 py-3 flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-blue-400/10 flex items-center justify-center text-lg font-mono font-bold text-blue-400">{{ stats.processing }}</div>
        <div>
          <div class="text-[10px] text-slate-500">处理中</div>
          <div class="text-xs text-white font-mono">处理中</div>
        </div>
      </div>
      <div class="glass rounded-xl px-4 py-3 flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-cyber-green/10 flex items-center justify-center text-lg font-mono font-bold text-cyber-green">{{ stats.done }}</div>
        <div>
          <div class="text-[10px] text-slate-500">已完成</div>
          <div class="text-xs text-white font-mono">已完成</div>
        </div>
      </div>
    </div>

    <!-- Filters -->
    <div class="flex gap-3 shrink-0">
      <select v-model="filterStatus" class="select-dark px-3 py-1.5 rounded-lg bg-slate-800 border border-white/10 text-sm text-white focus:outline-none focus:border-cyber-green/50">
        <option value="ALL">全部状态</option>
        <option value="PENDING">待处理</option>
        <option value="PROCESSING">处理中</option>
        <option value="DONE">已完成</option>
        <option value="IGNORED">已忽略</option>
      </select>
      <select v-model="filterSeverity" class="select-dark px-3 py-1.5 rounded-lg bg-slate-800 border border-white/10 text-sm text-white focus:outline-none focus:border-cyber-green/50">
        <option value="ALL">全部等级</option>
        <option value="CRITICAL">紧急</option>
        <option value="HIGH">高危</option>
        <option value="MEDIUM">中等</option>
        <option value="LOW">低</option>
      </select>
      <div class="flex items-center gap-2">
        <input
          v-model="filterDateStart"
          type="date"
          class="px-3 py-1.5 rounded-lg bg-slate-800 border border-white/10 text-sm text-white focus:outline-none focus:border-cyber-green/50"
        />
        <span class="text-slate-500 text-xs">至</span>
        <input
          v-model="filterDateEnd"
          type="date"
          class="px-3 py-1.5 rounded-lg bg-slate-800 border border-white/10 text-sm text-white focus:outline-none focus:border-cyber-green/50"
        />
      </div>
      <button
        v-if="filterStatus !== 'ALL' || filterSeverity !== 'ALL' || filterDateStart || filterDateEnd"
        class="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-white/5 transition-colors"
        @click="filterStatus = 'ALL'; filterSeverity = 'ALL'; filterDateStart = ''; filterDateEnd = ''"
      >
        重置筛选
      </button>
    </div>

    <!-- Work order list -->
    <GlassCard class="flex-1 min-h-0 overflow-y-auto">
      <!-- Loading state -->
      <div v-if="woStore.loading && woStore.orders.length === 0" class="flex items-center justify-center h-32">
        <div class="text-slate-500 text-sm font-mono">加载中...</div>
      </div>
      <!-- Error state -->
      <div v-else-if="woStore.error && woStore.orders.length === 0" class="flex flex-col items-center justify-center h-32 gap-2">
        <div class="text-sakura text-sm">{{ woStore.error }}</div>
        <button class="text-xs text-cyber-green hover:underline" @click="woStore.fetchOrders()">重试</button>
      </div>
      <!-- Empty state -->
      <div v-else-if="woStore.orders.length === 0" class="flex items-center justify-center h-32">
        <div class="text-slate-500 text-sm font-mono">暂无工单数据</div>
      </div>
      <div v-else class="space-y-3">
        <div
          v-for="order in filteredOrders"
          :key="order.id"
          class="glass rounded-xl p-4 hover:border-white/20 transition-all cursor-pointer"
          :class="severityConfig[order.severity]?.glow"
          @click="openDetail(order)"
        >
          <div class="flex items-start justify-between mb-3">
            <div class="flex-1">
              <div class="text-sm font-medium mb-1">
                <span v-if="order.severity === 'CRITICAL'" class="text-sakura">【紧急】</span>
                <span v-else-if="order.severity === 'HIGH'" class="text-orange-400">【高危】</span>
                <span v-else class="text-white">【{{ severityConfig[order.severity]?.label }}】</span>
                <span class="text-white">{{ order.title.replace(/^【[^】]*】/, '') }}</span>
              </div>
              <div class="flex items-center gap-3 text-xs text-slate-500">
                <span class="font-mono">网格: {{ order.gridLabel }}</span>
                <span class="font-mono">置信度: {{ (order.confidence * 100).toFixed(0) }}%</span>
                <span>{{ order.assignedToName }}</span>
              </div>
            </div>
            <div class="flex gap-2">
              <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="severityConfig[order.severity]?.color">
                {{ severityConfig[order.severity]?.label }}
              </span>
              <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="getStatusConf(order.status).color">
                {{ getStatusConf(order.status).label }}
              </span>
            </div>
          </div>

          <!-- Status flow visualization -->
          <div class="flex items-center gap-2 mt-3">
            <template v-for="(step, idx) in getStatusSteps(order)" :key="step.key">
              <div class="flex items-center gap-2">
                <div
                  class="w-2 h-2 rounded-full transition-all"
                  :class="step.active ? step.color : 'bg-white/10'"
                  :style="{ opacity: step.active ? 1 : 0.3 }"
                />
                <span class="text-[10px] text-slate-500">{{ step.label }}</span>
              </div>
              <div v-if="idx < 2" class="w-8 h-px bg-white/10" />
            </template>
            <div v-if="order.status === 'IGNORED'" class="ml-2 px-2 py-0.5 rounded text-[10px] font-mono bg-white/5 text-slate-500">
              已忽略
            </div>
          </div>

          <div class="flex justify-between items-center mt-3 pt-3 border-t border-white/5">
            <span class="text-[10px] text-slate-600 font-mono">{{ order.createdAt.replace('T', ' ').slice(0, 16) }}</span>
            <div class="flex gap-2">
              <button
                v-if="order.status === 'PENDING'"
                class="text-xs text-cyber-green hover:underline"
                @click.stop="updateOrderStatus(order.id, 'PROCESSING')"
              >
                确认处理
              </button>
              <button
                v-if="order.status === 'PROCESSING'"
                class="text-xs text-cyber-green hover:underline"
                @click.stop="updateOrderStatus(order.id, 'DONE')"
              >
                标记完成
              </button>
              <button class="text-xs text-slate-400 hover:underline" @click.stop="openDetail(order)">查看详情</button>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- Create Order Modal -->
    <Teleport to="body">
      <div
        v-if="showCreateModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @mousedown.self="closeCreateModal"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[480px] mx-4 shadow-2xl border border-white/10">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-lg font-bold text-white">手动创建工单</h2>
              <p class="text-xs text-slate-500 font-mono">CREATE WORKORDER</p>
            </div>
            <button
              class="w-8 h-8 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-white transition-colors"
              @click="closeCreateModal"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 6l6 6M6 12L6 6l6 6" />
              </svg>
            </button>
          </div>

          <form @submit.prevent="createOrder" class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">网格区域 <span class="text-sakura">*</span></label>
                <select
                  v-model="newOrder.gridLabel"
                  class="select-dark w-full px-4 py-3 rounded-xl bg-slate-800 border text-white text-sm focus:outline-none focus:border-cyber-green/50"
                  :class="formErrors.gridLabel ? 'border-sakura/50' : 'border-white/10'"
                >
                  <option value="" disabled>请选择网格</option>
                  <option v-for="g in gridOptions" :key="g" :value="g">{{ g }}</option>
                </select>
                <p v-if="formErrors.gridLabel" class="text-xs text-sakura mt-1">{{ formErrors.gridLabel }}</p>
              </div>
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">类型</label>
                <select
                  v-model="newOrder.type"
                  class="select-dark w-full px-4 py-3 rounded-xl bg-slate-800 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50"
                >
                  <option value="disease">病害</option>
                  <option value="pest">虫害</option>
                </select>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">病虫害名称 <span class="text-sakura">*</span></label>
                <input
                  v-model="newOrder.pestName"
                  type="text"
                  placeholder="如: 红蜘蛛"
                  class="w-full px-4 py-3 rounded-xl bg-white/5 border text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
                  :class="formErrors.pestName ? 'border-sakura/50' : 'border-white/10'"
                />
                <p v-if="formErrors.pestName" class="text-xs text-sakura mt-1">{{ formErrors.pestName }}</p>
              </div>
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">严重程度</label>
                <select
                  v-model="newOrder.severity"
                  class="select-dark w-full px-4 py-3 rounded-xl bg-slate-800 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50"
                >
                  <option value="CRITICAL">紧急</option>
                  <option value="HIGH">高危</option>
                  <option value="MEDIUM">中等</option>
                  <option value="LOW">低</option>
                </select>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">指派专家</label>
                <select
                  v-model="newOrder.assignedTo"
                  class="select-dark w-full px-4 py-3 rounded-xl bg-slate-800 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50"
                  :disabled="expertsLoading"
                >
                  <option value="">未分配</option>
                  <option v-for="expert in experts" :key="expert.id" :value="expert.id">
                    {{ expert.name }}
                  </option>
                </select>
                <p v-if="expertsLoading" class="text-xs text-slate-500 mt-1">加载专家列表中...</p>
              </div>
            </div>

            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">置信度</label>
              <div class="flex items-center gap-4">
                <input
                  v-model.number="newOrder.confidence"
                  type="range"
                  min="0"
                  max="1"
                  step="0.01"
                  class="flex-1"
                />
                <span class="text-sm font-mono text-white w-16 text-right">{{ (newOrder.confidence * 100).toFixed(0) }}%</span>
              </div>
            </div>

            <div class="flex gap-3 pt-2">
              <GlowButton label="创建工单" type="submit" class="flex-1" />
              <button
                type="button"
                class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
                @click="closeCreateModal"
              >
                取消
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Detail Modal -->
    <Teleport to="body">
      <div
        v-if="showDetailModal && selectedOrder"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @click.self="closeDetail"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[520px] mx-4 shadow-2xl border border-white/10">
          <div class="flex items-center justify-between mb-6">
            <div class="flex items-center gap-3">
              <div class="w-12 h-12 rounded-xl flex items-center justify-center font-mono font-bold text-white text-lg" :class="severityConfig[selectedOrder.severity]?.color">
                {{ selectedOrder.gridLabel }}
              </div>
              <div>
                <h2 class="text-lg font-bold text-white">{{ selectedOrder.title }}</h2>
                <p class="text-xs text-slate-500 font-mono">WORKORDER DETAIL</p>
              </div>
            </div>
            <div class="flex gap-2">
              <button
                v-if="selectedOrder.imageUrl"
                class="w-8 h-8 rounded-lg bg-blue-400/10 hover:bg-blue-400/20 flex items-center justify-center text-blue-400 transition-colors"
                title="查看图片"
                @click="showImageModal = true"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21zM10.5 8.25a1.125 1.125 0 11-2.25 0 1.125 1.125 0 012.25 0z" />
                </svg>
              </button>
              <button
                v-if="!isExpert"
                class="w-8 h-8 rounded-lg bg-cyber-green/10 hover:bg-cyber-green/20 flex items-center justify-center text-cyber-green transition-colors"
                title="发送邮件给专家"
                @click="sendEmailToExpert()"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
                </svg>
              </button>
              <button
                class="w-8 h-8 rounded-lg bg-sakura/10 hover:bg-sakura/20 flex items-center justify-center text-sakura hover:text-white transition-colors"
                @click="closeDetail"
              >
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          <div class="space-y-4">
            <!-- Status badges -->
            <div class="flex gap-2">
              <span class="px-3 py-1 rounded-lg text-xs font-mono border" :class="severityConfig[selectedOrder.severity]?.color">
                {{ severityConfig[selectedOrder.severity]?.label }}
              </span>
              <span class="px-3 py-1 rounded-lg text-xs font-mono border" :class="getStatusConf(selectedOrder.status).color">
                {{ getStatusConf(selectedOrder.status).label }}
              </span>
            </div>

            <!-- Details grid -->
            <div class="grid grid-cols-2 gap-4">
              <div class="glass rounded-lg px-4 py-3">
                <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">网格区域</div>
                <div class="text-sm font-mono text-white">{{ selectedOrder.gridLabel }}</div>
              </div>
              <div class="glass rounded-lg px-4 py-3">
                <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">病虫害</div>
                <div class="text-sm font-mono text-white">{{ selectedOrder.pestName || '无' }}</div>
              </div>
              <div class="glass rounded-lg px-4 py-3">
                <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">置信度</div>
                <div class="text-sm font-mono text-white">{{ (selectedOrder.confidence * 100).toFixed(0) }}%</div>
              </div>
              <div class="glass rounded-lg px-4 py-3 assignee-selector" ref="assigneeFieldRef">
                <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">指派专家</div>
                <div
                  v-if="!isExpert && selectedOrder.status !== 'DONE' && selectedOrder.status !== 'IGNORED'"
                  class="text-sm font-mono text-white cursor-pointer hover:text-cyber-green transition-colors"
                  @click="toggleAssigneeSelector"
                >
                  {{ selectedOrder.assignedToName }}
                  <svg class="w-3 h-3 inline-block ml-1" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="M19 9l-7 7-7-7" />
                  </svg>
                </div>
                <div v-else class="text-sm font-mono text-white">
                  {{ selectedOrder.assignedToName }}
                </div>
              </div>
              <div class="glass rounded-lg px-4 py-3">
                <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">创建时间</div>
                <div class="text-sm font-mono text-white">{{ selectedOrder.createdAt.replace('T', ' ').slice(0, 16) }}</div>
              </div>
              <div class="glass rounded-lg px-4 py-3">
                <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">更新时间</div>
                <div class="text-sm font-mono text-white">{{ selectedOrder.updatedAt.replace('T', ' ').slice(0, 16) }}</div>
              </div>
            </div>

            <!-- Status flow -->
            <div class="glass rounded-lg px-4 py-3">
              <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-3">状态流转</div>
              <div class="flex items-center justify-between">
                <div
                  v-for="(step, idx) in getStatusSteps(selectedOrder)"
                  :key="step.key"
                  class="flex flex-col items-center gap-2"
                >
                  <div
                    class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-mono font-bold"
                    :class="step.active ? `${step.color} text-white` : 'bg-white/10 text-slate-500'"
                  >
                    {{ idx + 1 }}
                  </div>
                  <span class="text-[10px] text-slate-500">{{ step.label }}</span>
                </div>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex flex-wrap gap-3 pt-2">
              <!-- 待处理状态 -->
              <button
                v-if="selectedOrder.status === 'PENDING'"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors"
                @click="updateOrderStatus(selectedOrder.id, 'PROCESSING')"
              >
                确认处理
              </button>
              <button
                v-if="selectedOrder.status === 'PENDING'"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-slate-400 text-sm hover:bg-white/10 transition-colors"
                @click="updateOrderStatus(selectedOrder.id, 'IGNORED')"
              >
                忽略
              </button>

              <!-- 处理中状态 -->
              <button
                v-if="selectedOrder.status === 'PROCESSING'"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors"
                @click="updateOrderStatus(selectedOrder.id, 'DONE')"
              >
                标记完成
              </button>

              <!-- 已忽略状态 -->
              <button
                v-if="selectedOrder.status === 'IGNORED'"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-blue-400/10 border border-blue-400/20 text-blue-400 text-sm hover:bg-blue-400/20 transition-colors"
                @click="updateOrderStatus(selectedOrder.id, 'PENDING')"
              >
                恢复待处理
              </button>

              <!-- 升级/降级等级（仅管理员可用） -->
              <button
                v-if="!isExpert && (selectedOrder.status === 'PENDING' || selectedOrder.status === 'PROCESSING') && severityConfig[selectedOrder.severity]?.level < 4"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-orange-400/10 border border-orange-400/20 text-orange-400 text-sm hover:bg-orange-400/20 transition-colors"
                @click="async () => { try { await woStore.escalateSeverity(selectedOrder.id); selectedOrder.value = woStore.orders.find(o => o.id === selectedOrder.id) } catch {} }"
              >
                升级等级
              </button>
              <button
                v-if="!isExpert && (selectedOrder.status === 'PENDING' || selectedOrder.status === 'PROCESSING') && severityConfig[selectedOrder.severity]?.level > 1"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-slate-400/10 border border-slate-400/20 text-slate-400 text-sm hover:bg-slate-400/20 transition-colors"
                @click="async () => { try { await woStore.deescalateSeverity(selectedOrder.id); selectedOrder.value = woStore.orders.find(o => o.id === selectedOrder.id) } catch {} }"
              >
                降级等级
              </button>

              <!-- 确认修改（仅管理员可用） -->
              <button
                v-if="!isExpert && selectedOrder.status !== 'DONE' && selectedOrder.status !== 'IGNORED'"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors"
                @click="confirmUpdate"
              >
                确认修改
              </button>

              <!-- 删除工单（仅管理员可用） -->
              <button
                v-if="!isExpert"
                class="flex-1 min-w-[100px] px-4 py-3 rounded-xl bg-sakura/10 border border-sakura/20 text-sakura text-sm hover:bg-sakura/20 transition-colors"
                @click="openDeleteConfirm(selectedOrder)"
              >
                删除工单
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Expert Selector Dropdown (Teleported to body) -->
    <Teleport to="body">
      <div
        v-if="showAssigneeSelector"
        class="fixed bg-slate-800 border border-white/10 rounded-lg shadow-lg assignee-dropdown"
        :style="assigneeDropdownStyle"
        @mousedown.stop
        @click.stop
      >
        <div class="p-2">
          <input
            v-model="expertSearchQuery"
            type="text"
            placeholder="搜索专家..."
            class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50"
            @click.stop
          />
        </div>
        <div class="max-h-40 overflow-y-auto">
          <div v-if="woStore.expertsLoading" class="px-3 py-2 text-slate-500 text-xs">
            加载中...
          </div>
          <div v-else-if="woStore.expertsError" class="px-3 py-2 text-sakura text-xs">
            {{ woStore.expertsError }}
          </div>
          <div v-else-if="filteredExperts.length === 0" class="px-3 py-2 text-slate-500 text-xs">
            无匹配专家
          </div>
          <div
            v-for="expert in filteredExperts"
            :key="expert.id"
            class="px-3 py-2 text-sm text-white hover:bg-white/10 cursor-pointer transition-colors"
            :class="{ 'bg-cyber-green/10 text-cyber-green': expert.name === selectedOrder?.assignedToName }"
            @click="selectExpert(expert)"
          >
            {{ expert.name }}
            <span class="text-xs text-slate-500 ml-2">{{ expert.email }}</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm && selectedOrder"
        class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm"
        @mousedown.self="closeDeleteConfirm"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[400px] mx-4 shadow-2xl border border-sakura/20">
          <div class="text-center">
            <div class="w-16 h-16 rounded-full bg-sakura/10 flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-sakura" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <h3 class="text-lg font-bold text-white mb-2">确认删除工单</h3>
            <p class="text-sm text-slate-400 mb-2">您即将删除以下工单：</p>
            <p class="text-sm font-mono text-white bg-white/5 rounded-lg px-3 py-2 mb-4">{{ selectedOrder.title }}</p>
            <p class="text-xs text-sakura mb-6">此操作不可撤销，请谨慎操作！</p>
            <div class="flex gap-3">
              <button
                class="flex-1 px-4 py-3 rounded-xl bg-sakura/10 border border-sakura/20 text-sakura text-sm hover:bg-sakura/20 transition-colors"
                @click="deleteOrder"
              >
                确认删除
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

    <!-- Email Modal -->
    <Teleport to="body">
      <div
        v-if="showEmailModal && selectedOrder"
        class="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 backdrop-blur-sm"
        @mousedown.self="closeEmailModal"
      >
        <div class="glass rounded-2xl p-6 w-full max-w-[560px] mx-4 shadow-2xl border border-white/10">
          <div class="flex items-center justify-between mb-5">
            <div>
              <h2 class="text-lg font-bold text-white">发送工单邮件</h2>
              <p class="text-xs text-slate-500 font-mono">SEND WORKORDER EMAIL</p>
            </div>
            <button
              class="w-8 h-8 rounded-lg bg-sakura/10 hover:bg-sakura/20 flex items-center justify-center text-sakura hover:text-white transition-colors"
              @click="closeEmailModal"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- Loading 状态 -->
          <div v-if="emailPreviewLoading" class="flex flex-col items-center justify-center py-12">
            <div class="w-8 h-8 border-2 border-cyber-green/30 border-t-cyber-green rounded-full animate-spin mb-4" />
            <p class="text-slate-400 text-sm">AI 正在生成邮件内容...</p>
          </div>

          <!-- 错误状态 -->
          <div v-else-if="emailError" class="text-center py-8">
            <div class="px-4 py-3 rounded-xl bg-sakura/10 border border-sakura/20 text-sakura text-sm mb-4">
              {{ emailError }}
            </div>
            <button
              class="px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
              @click="closeEmailModal"
            >
              关闭
            </button>
          </div>

          <!-- 预览编辑态 -->
          <div v-else-if="emailPreviewData" class="space-y-4">
            <!-- 收件人 -->
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">收件人</label>
              <div class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm">
                {{ emailPreviewData.toName }} ({{ emailPreviewData.toEmail }})
              </div>
            </div>

            <!-- 主题 -->
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">邮件主题</label>
              <div class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm">
                {{ emailPreviewData.subject }}
              </div>
            </div>

            <!-- 邮件内容（可编辑） -->
            <div>
              <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">邮件内容（AI 生成，可修改）</label>
              <textarea
                v-model="emailContent"
                rows="10"
                placeholder="邮件内容"
                class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all resize-none"
              />
            </div>

            <!-- 发送结果 -->
            <div v-if="emailResult === 'success'" class="px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-xs">
              邮件发送成功！
            </div>
            <div v-else-if="emailResult && emailResult !== 'success'" class="px-4 py-3 rounded-xl bg-sakura/10 border border-sakura/20 text-sakura text-xs">
              发送失败：{{ emailResult }}
            </div>

            <!-- 操作按钮 -->
            <div class="flex gap-3 pt-2">
              <button
                class="flex-1 px-4 py-3 rounded-xl text-sm transition-colors"
                :class="emailContent && !emailSending && emailResult !== 'success' ? 'bg-cyber-green/10 border border-cyber-green/20 text-cyber-green hover:bg-cyber-green/20' : 'bg-white/5 border border-white/10 text-slate-600 cursor-not-allowed'"
                :disabled="!emailContent || emailSending || emailResult === 'success'"
                @click="sendEmail"
              >
                {{ emailSending ? '发送中...' : '确认发送' }}
              </button>
              <button
                class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm hover:bg-white/10 transition-colors"
                @click="closeEmailModal"
              >
                {{ emailResult === 'success' ? '关闭' : '取消' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Image Viewer Modal -->
    <Teleport to="body">
      <div
        v-if="showImageModal && selectedOrder?.imageUrl"
        class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 backdrop-blur-sm"
        @click.self="showImageModal = false"
      >
        <div class="relative max-w-[90vw] max-h-[90vh]">
          <button
            class="absolute -top-3 -right-3 w-8 h-8 rounded-full bg-slate-800 border border-white/10 hover:bg-sakura/20 hover:border-sakura/30 flex items-center justify-center text-slate-400 hover:text-white transition-colors z-10"
            @click="showImageModal = false"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
          <img
            :src="selectedOrder.imageUrl"
            :alt="selectedOrder.title"
            class="max-w-[90vw] max-h-[85vh] rounded-xl object-contain shadow-2xl border border-white/10"
          />
          <div class="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/60 to-transparent rounded-b-xl px-4 py-3">
            <p class="text-sm text-white font-medium">{{ selectedOrder.title }}</p>
            <p class="text-[10px] text-slate-300 font-mono">Grid-{{ selectedOrder.gridLabel }} · {{ selectedOrder.pestName }} · {{ (selectedOrder.confidence * 100).toFixed(0) }}%</p>
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

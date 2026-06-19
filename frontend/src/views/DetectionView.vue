<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import { useWorkOrderStore } from '../stores/workorder'
import type { ExpertVO } from '../api/workorder'

interface Detection {
  class_name: string
  name_cn: string
  confidence: number
  bbox: { x: number; y: number; width: number; height: number }
}

interface SingleResult {
  disease: { detections: Detection[]; count: number; elapsed_ms: number }
  pest: { detections: Detection[]; count: number; elapsed_ms: number }
  annotated_url: string | null
  original_url: string | null
  total_elapsed_ms: number
  error?: string | null
}

interface BatchItem {
  file: File
  previewUrl: string
  result: SingleResult | null
  annotatedUrl: string
  error: string | null
}

const MAX_FILES = 20
const MAX_SIZE = 10 * 1024 * 1024 // 10MB

const files = ref<BatchItem[]>([])
const isUploading = ref(false)
const error = ref('')
const dragOver = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const activeIndex = ref(0)
const uploadProgress = ref({ done: 0, total: 0 })
const showEnlarged = ref(false)
const detectionMode = ref<'detect' | 'workorder'>('detect')

// ========== 摄像头拍照 ==========
const showCamera = ref(false)
const cameraStream = ref<MediaStream | null>(null)
const videoRef = ref<HTMLVideoElement>()
const canvasRef = ref<HTMLCanvasElement>()
const cameraError = ref('')

// ========== 工单创建 ==========
const workOrderStore = useWorkOrderStore()
const showWorkOrderModal = ref(false)
const workOrderSubmitting = ref(false)
const workOrderSuccess = ref(false)
const workOrderForm = ref({
  title: '',
  severity: 'MEDIUM' as string,
  type: '' as string,
  pestName: '' as string,
  confidence: 0,
  assignedTo: '' as string,
})
const workOrderDetectionIndex = ref(-1) // 当前正在创建工单的检测项索引
const workOrderImageIndex = ref(-1) // 当前正在创建工单的图片索引

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    showEnlarged.value = false
    showWorkOrderModal.value = false
    if (showCamera.value) closeCamera()
  }
}
onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  workOrderStore.fetchExperts()
  workOrderStore.fetchManagers()
  workOrderStore.fetchStaff()
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  closeCamera()
})

const isBatch = computed(() => files.value.length > 1)
const activeItem = computed(() => files.value[activeIndex.value] || null)
const hasResults = computed(() => files.value.some(f => f.result))

function onFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  addFiles(Array.from(input.files))
  input.value = ''
}

function addFiles(newFiles: File[]) {
  error.value = ''
  const valid = newFiles.filter(f => {
    if (!f.type.startsWith('image/')) return false
    if (f.size > MAX_SIZE) return false
    return true
  })
  if (valid.length === 0) {
    error.value = '请选择 JPG/PNG 格式且小于 10MB 的图片'
    return
  }
  const combined = [...files.value.map(f => f.file), ...valid].slice(0, MAX_FILES)
  files.value = combined.map(f => ({
    file: f,
    previewUrl: URL.createObjectURL(f),
    result: null,
    annotatedUrl: '',
    error: null,
  }))
  activeIndex.value = 0
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  const dropped = Array.from(e.dataTransfer?.files || [])
  if (dropped.length) addFiles(dropped)
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragOver.value = true
}

function onDragLeave() {
  dragOver.value = false
}

function removeFile(index: number) {
  URL.revokeObjectURL(files.value[index].previewUrl)
  files.value.splice(index, 1)
  if (activeIndex.value >= files.value.length) activeIndex.value = Math.max(0, files.value.length - 1)
}

function clearAll() {
  files.value.forEach(f => {
    URL.revokeObjectURL(f.previewUrl)
  })
  files.value = []
  activeIndex.value = 0
  error.value = ''
  uploadProgress.value = { done: 0, total: 0 }
}

// ========== 摄像头拍照 ==========
async function openCamera() {
  cameraError.value = ''
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } },
      audio: false,
    })
    cameraStream.value = stream
    showCamera.value = true
    // 等 DOM 更新后绑定 stream
    await nextTick()
    if (videoRef.value) {
      videoRef.value.srcObject = stream
    }
  } catch (err: any) {
    if (err.name === 'NotAllowedError') {
      cameraError.value = '摄像头权限被拒绝，请在浏览器设置中允许访问摄像头'
    } else if (err.name === 'NotFoundError') {
      cameraError.value = '未检测到摄像头设备'
    } else {
      cameraError.value = `摄像头打开失败: ${err.message}`
    }
  }
}

function capturePhoto() {
  const video = videoRef.value
  const canvas = canvasRef.value
  if (!video || !canvas) return

  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const ctx = canvas.getContext('2d')!
  ctx.drawImage(video, 0, 0)

  canvas.toBlob((blob) => {
    if (!blob) return
    const file = new File([blob], `camera_${Date.now()}.jpg`, { type: 'image/jpeg' })
    addFiles([file])
    closeCamera()
  }, 'image/jpeg', 0.92)
}

function closeCamera() {
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach(t => t.stop())
    cameraStream.value = null
  }
  showCamera.value = false
  cameraError.value = ''
}

// ========== 单张检测 ==========
async function detectSingle(item: BatchItem): Promise<SingleResult> {
  const base64 = await fileToBase64(item.file)
  const response = await fetch('/api/v1/detect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      image: { type: 'base64', data: base64 },
      confidence: 0.1,
    }),
  })
  if (!response.ok) {
    const err = await response.json().catch(() => ({}))
    throw new Error(err.message || `请求失败: ${response.statusText}`)
  }
  const data = await response.json()
  if (data.code !== 200) throw new Error(data.message || '检测失败')
  return data.data
}

// ========== 统一入口 ==========
async function handleDetect() {
  if (!files.value.length) return
  isUploading.value = true
  error.value = ''

  const total = files.value.length
  uploadProgress.value = { done: 0, total }

  try {
    // 统一使用单张逐个检测，避免批量接口超时/失败
    for (let i = 0; i < total; i++) {
      const item = files.value[i]
      try {
        item.result = await detectSingle(item)
        if (item.result.annotated_url) {
          item.annotatedUrl = item.result.annotated_url
        }
      } catch (err: any) {
        item.error = err.message
      }
      uploadProgress.value = { done: i + 1, total }
    }
  } catch (err: any) {
    error.value = err.message || '检测请求失败，请检查推理服务是否运行'
  } finally {
    isUploading.value = false
  }
}

// ========== 工具函数 ==========
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve((reader.result as string).split(',')[1])
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// ========== 工单创建逻辑 ==========
const AUTO_ASSIGN_THRESHOLD = 0.6

/** 根据置信度自动选择负责人：>0.6 选基层员工，≤0.6 选专家 */
function autoAssignee(confidence: number): string {
  if (confidence > AUTO_ASSIGN_THRESHOLD) {
    return workOrderStore.staffList.length > 0 ? workOrderStore.staffList[0].id : ''
  }
  return workOrderStore.experts.length > 0 ? workOrderStore.experts[0].id : ''
}

/** 根据置信度获取推荐角色名 */
function recommendedRoleLabel(confidence: number): string {
  return confidence > AUTO_ASSIGN_THRESHOLD ? '基层员工' : '专家'
}

/** 根据置信度推断严重程度 */
function inferSeverity(confidence: number): string {
  if (confidence >= 0.8) return 'CRITICAL'
  if (confidence >= 0.6) return 'HIGH'
  if (confidence >= 0.4) return 'MEDIUM'
  return 'LOW'
}

/** 打开工单创建弹窗 */
function openWorkOrderModal(det: Detection, detIndex: number, imgIndex: number, pipeline: string) {
  workOrderDetectionIndex.value = detIndex
  workOrderImageIndex.value = imgIndex
  workOrderSuccess.value = false
  workOrderForm.value = {
    title: `【${inferSeverity(det.confidence)}】${det.name_cn} 工单`,
    severity: inferSeverity(det.confidence),
    type: pipeline,
    pestName: det.name_cn,
    confidence: det.confidence,
    assignedTo: 'auto',
  }
  showWorkOrderModal.value = true
}

/** 提交工单 */
async function submitWorkOrder() {
  workOrderSubmitting.value = true
  try {
    // 解析负责人：auto 时根据置信度实时选择
    let assignedTo: string | undefined
    if (workOrderForm.value.assignedTo === 'auto') {
      assignedTo = autoAssignee(workOrderForm.value.confidence) || undefined
    } else {
      assignedTo = workOrderForm.value.assignedTo || undefined
    }

    // 根据指派角色选择主图：EXPERT 用原始图，其余用标注图
    // 同时传递两张图片URL，后端创建 inference 记录存储，前端按角色动态切换
    let imageUrl: string | undefined
    let originalImageUrl: string | undefined
    if (activeItem.value?.result) {
      const effectiveUserId = assignedTo
      const assignedUser = assignableUsers.value.find(u => u.id === effectiveUserId)
      originalImageUrl = activeItem.value.result.original_url || undefined
      if (assignedUser?.role === 'EXPERT') {
        imageUrl = activeItem.value.result.original_url || undefined
      } else {
        imageUrl = activeItem.value.result.annotated_url || undefined
      }
    }

    await workOrderStore.addOrder({
      title: workOrderForm.value.title,
      severity: workOrderForm.value.severity,
      type: workOrderForm.value.type,
      pestName: workOrderForm.value.pestName,
      confidence: workOrderForm.value.confidence,
      assignedTo,
      imageUrl,
      originalImageUrl,
    })
    workOrderSuccess.value = true
    setTimeout(() => {
      showWorkOrderModal.value = false
      workOrderSuccess.value = false
    }, 1500)
  } catch {
    // error is handled in store
  } finally {
    workOrderSubmitting.value = false
  }
}

/** 合并专家、管理员和基层员工列表供下拉选择 */
const assignableUsers = computed<ExpertVO[]>(() => {
  const experts = workOrderStore.experts
  const managers = workOrderStore.managers
  const staff = workOrderStore.staffList
  const ids = new Set(experts.map(e => e.id))
  const result = [...experts, ...managers.filter(m => !ids.has(m.id))]
  for (const s of staff) {
    if (!ids.has(s.id)) {
      ids.add(s.id)
      result.push(s)
    }
  }
  return result
})

function getSeverityColor(confidence: number): string {
  if (confidence >= 0.8) return 'text-[#EF4444]'
  if (confidence >= 0.6) return 'text-[#FF6A00]'
  return 'text-[#4ADE80]'
}

function getSeverityBg(confidence: number): string {
  if (confidence >= 0.8) return 'bg-[#EF4444]/10 border-[#EF4444]/20'
  if (confidence >= 0.6) return 'bg-[#FF6A00]/10 border-[#FF6A00]/20'
  return 'bg-[#4ADE80]/10 border-[#4ADE80]/20'
}

function totalDetections(item: BatchItem): number {
  if (!item.result) return 0
  return item.result.disease.count + item.result.pest.count
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">图像检测</h1>
        <p class="text-xs text-slate-500 font-mono">AI-POWERED DISEASE & PEST DETECTION</p>
      </div>
      <!-- Mode toggle -->
      <div class="flex items-center gap-1 p-1 rounded-xl bg-white/5 border border-white/10">
        <button
          class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
          :class="detectionMode === 'detect'
            ? 'bg-white/10 text-white shadow-sm'
            : 'text-slate-500 hover:text-slate-300'"
          @click="detectionMode = 'detect'"
        >
          仅检测
        </button>
        <button
          class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
          :class="detectionMode === 'workorder'
            ? 'bg-gradient-to-r from-[#FF6A00]/20 to-[#FFB300]/20 text-[#FF6A00] border border-[#FF6A00]/20 shadow-sm'
            : 'text-slate-500 hover:text-slate-300'"
          @click="detectionMode = 'workorder'"
        >
          检测并生成工单
        </button>
      </div>
    </div>

    <div class="flex-1 min-h-0 grid grid-cols-2 gap-4 overflow-hidden">
      <!-- Left: Upload area -->
      <GlassCard class="flex flex-col overflow-hidden">
        <div class="flex items-center justify-between mb-3 shrink-0">
          <span class="text-xs text-slate-400 tracking-wider">
            图片上传
            <span v-if="files.length" class="text-slate-600 ml-2">{{ files.length }} 张</span>
          </span>
          <div class="flex gap-2">
            <button
              v-if="files.length"
              class="text-[10px] text-slate-500 hover:text-sakura transition-colors"
              @click="clearAll"
            >
              清空
            </button>
            <button
              class="text-[10px] text-slate-500 hover:text-white transition-colors flex items-center gap-1"
              @click="openCamera"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M6.827 6.175A2.31 2.31 0 015.186 7.23c-.38.054-.757.112-1.134.175C2.999 7.58 2.25 8.507 2.25 9.574V18a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9.574c0-1.067-.75-1.994-1.802-2.169a47.865 47.865 0 00-1.134-.175 2.31 2.31 0 01-1.64-1.055l-.822-1.316a2.192 2.192 0 00-1.736-1.039 48.774 48.774 0 00-5.232 0 2.192 2.192 0 00-1.736 1.039l-.821 1.316z" />
                <path d="M16.5 12.75a4.5 4.5 0 11-9 0 4.5 4.5 0 019 0zM18.75 10.5h.008v.008h-.008V10.5z" />
              </svg>
              拍照
            </button>
          </div>
        </div>

        <!-- Drop zone / Preview -->
        <div
          class="flex-1 min-h-0 rounded-xl border-2 border-dashed transition-all duration-200 overflow-hidden"
          :class="dragOver
            ? 'border-[#FF6A00]/50 bg-[#FF6A00]/5'
            : files.length
              ? 'border-white/10 bg-white/[0.02]'
              : 'border-white/10 bg-white/[0.02] hover:border-white/20 hover:bg-white/[0.04] cursor-pointer'"
          @drop.prevent="onDrop"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @click="!files.length && fileInputRef?.click()"
        >
          <!-- Single image preview -->
          <div v-if="files.length === 1" class="relative w-full h-full p-2">
            <img :src="files[0].previewUrl" class="w-full h-full object-contain rounded-lg" />
            <button
              class="absolute top-3 right-3 w-7 h-7 rounded-lg bg-black/60 backdrop-blur flex items-center justify-center text-slate-400 hover:text-white transition-colors"
              @click.stop="removeFile(0)"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
          </div>

          <!-- Batch: active image + thumbnail strip -->
          <div v-else-if="files.length > 1" class="flex flex-col h-full">
            <!-- Active image -->
            <div class="flex-1 min-h-0 relative p-2">
              <img
                v-if="activeItem"
                :src="activeItem.previewUrl"
                class="w-full h-full object-contain rounded-lg"
              />
              <!-- Result badge on image -->
              <div
                v-if="activeItem?.result"
                class="absolute top-4 left-4 px-2 py-1 rounded-md text-[10px] font-mono border"
                :class="totalDetections(activeItem) > 0
                  ? 'bg-[#EF4444]/10 border-[#EF4444]/20 text-[#EF4444]'
                  : 'bg-[#4ADE80]/10 border-[#4ADE80]/20 text-[#4ADE80]'"
              >
                {{ totalDetections(activeItem) > 0 ? `检出 ${totalDetections(activeItem)} 项` : '健康' }}
              </div>
              <div
                v-if="activeItem?.error"
                class="absolute top-4 left-4 px-2 py-1 rounded-md text-[10px] font-mono border bg-[#EF4444]/10 border-[#EF4444]/20 text-[#EF4444]"
              >
                检测失败
              </div>
            </div>
            <!-- Thumbnail strip -->
            <div class="shrink-0 px-2 pb-2 flex gap-1.5 overflow-x-auto">
              <button
                v-for="(item, i) in files"
                :key="i"
                class="relative w-14 h-14 rounded-lg overflow-hidden border-2 shrink-0 transition-all"
                :class="i === activeIndex
                  ? 'border-[#FF6A00] shadow-lg shadow-[#FF6A00]/20'
                  : 'border-white/10 opacity-60 hover:opacity-100'"
                @click.stop="activeIndex = i"
              >
                <img :src="item.previewUrl" class="w-full h-full object-cover" />
                <!-- Status dot -->
                <div
                  v-if="item.result || item.error"
                  class="absolute top-0.5 right-0.5 w-2 h-2 rounded-full"
                  :class="item.error ? 'bg-[#EF4444]' : totalDetections(item) > 0 ? 'bg-[#FF6A00]' : 'bg-[#4ADE80]'"
                />
                <!-- Remove -->
                <button
                  class="absolute inset-0 bg-black/0 hover:bg-black/50 flex items-center justify-center opacity-0 hover:opacity-100 transition-all"
                  @click.stop="removeFile(i)"
                >
                  <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M6 18L18 6M6 6l12 12" /></svg>
                </button>
              </button>
              <!-- Add more -->
              <button
                v-if="files.length < MAX_FILES"
                class="w-14 h-14 rounded-lg border-2 border-dashed border-white/10 hover:border-white/20 flex items-center justify-center shrink-0 transition-all"
                @click.stop="fileInputRef?.click()"
              >
                <svg class="w-5 h-5 text-slate-600" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path d="M12 4.5v15m7.5-7.5h-15" /></svg>
              </button>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else class="w-full h-full flex flex-col items-center justify-center">
            <svg class="w-12 h-12 mx-auto mb-3 text-slate-600" fill="none" stroke="currentColor" stroke-width="1" viewBox="0 0 24 24">
              <path d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21z" />
            </svg>
            <p class="text-sm text-slate-400 mb-1">拖拽图片到此处或点击选择</p>
            <p class="text-[10px] text-slate-600">支持 JPG / PNG，单张最大 10MB，最多 20 张</p>
          </div>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png"
          multiple
          class="hidden"
          @change="onFileSelect"
        />

        <!-- Action -->
        <button
          class="mt-3 w-full py-3 rounded-xl text-sm font-bold transition-all disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
          :class="!files.length || isUploading
            ? 'bg-white/5 text-slate-500 border border-white/5'
            : 'bg-gradient-to-r from-[#FF6A00] to-[#FFB300] text-[#0B0F19] hover:shadow-lg hover:shadow-[#FF6A00]/20 hover:scale-[1.01]'"
          :disabled="!files.length || isUploading"
          @click="handleDetect"
        >
          {{ isUploading
            ? isBatch ? `检测中 ${uploadProgress.done}/${uploadProgress.total}...` : '正在检测...'
            : isBatch ? `批量检测 (${files.length} 张)` : '开始检测'
          }}
        </button>
      </GlassCard>

      <!-- Right: Results -->
      <GlassCard class="flex flex-col overflow-hidden">
        <div class="flex items-center justify-between mb-3 shrink-0">
          <span class="text-xs text-slate-400 tracking-wider">检测结果</span>
          <span v-if="isBatch && files.length > 1" class="text-[10px] text-slate-600 font-mono">
            {{ activeIndex + 1 }} / {{ files.length }}
          </span>
        </div>

        <!-- Error -->
        <div v-if="error" class="mb-3 px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs flex items-center gap-2 shrink-0">
          <svg class="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
          {{ error }}
        </div>

        <!-- Empty state -->
        <div v-if="!hasResults && !error && !isUploading" class="flex-1 flex flex-col items-center justify-center">
          <svg class="w-16 h-16 text-slate-700 mb-4" fill="none" stroke="currentColor" stroke-width="1" viewBox="0 0 24 24">
            <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
          </svg>
          <p class="text-sm text-slate-500">上传图片并点击检测，结果将在此显示</p>
        </div>

        <!-- Loading -->
        <div v-if="isUploading" class="flex-1 flex flex-col items-center justify-center">
          <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-[#FF6A00] to-[#FFB300] flex items-center justify-center mb-4 animate-pulse">
            <svg class="w-6 h-6 text-[#0B0F19]" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
            </svg>
          </div>
          <p class="text-sm text-white font-medium mb-1">AI 推理中...</p>
          <p class="text-[10px] text-slate-500">正在执行病害 + 虫害双模型检测</p>
        </div>

        <!-- Results -->
        <div v-if="activeItem && (activeItem.result || activeItem.error) && !isUploading" class="flex-1 min-h-0 overflow-y-auto space-y-3">
          <!-- Per-image error -->
          <div v-if="activeItem.error" class="px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs">
            {{ activeItem.error }}
          </div>

          <template v-if="activeItem.result">
            <!-- Annotated image -->
            <div
              v-if="activeItem.annotatedUrl"
              class="rounded-xl overflow-hidden border border-white/10 cursor-pointer group relative"
              @click="showEnlarged = true"
            >
              <img :src="activeItem.annotatedUrl" class="w-full object-contain max-h-48 transition-opacity group-hover:opacity-80" />
              <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-black/30">
                <svg class="w-8 h-8 text-white" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607zM10.5 7.5v6m3-3h-6" />
                </svg>
              </div>
            </div>

            <!-- Stats bar -->
            <div class="grid grid-cols-3 gap-2">
              <div class="rounded-lg bg-white/[0.03] border border-white/[0.06] p-2 text-center">
                <div class="text-lg font-bold font-mono text-white">{{ activeItem.result.disease.count + activeItem.result.pest.count }}</div>
                <div class="text-[10px] text-slate-500">总检出</div>
              </div>
              <div class="rounded-lg bg-[#EF4444]/5 border border-[#EF4444]/10 p-2 text-center">
                <div class="text-lg font-bold font-mono text-[#EF4444]">{{ activeItem.result.disease.count }}</div>
                <div class="text-[10px] text-slate-500">病害</div>
              </div>
              <div class="rounded-lg bg-[#FF6A00]/5 border border-[#FF6A00]/10 p-2 text-center">
                <div class="text-lg font-bold font-mono text-[#FF6A00]">{{ activeItem.result.pest.count }}</div>
                <div class="text-[10px] text-slate-500">虫害</div>
              </div>
            </div>

            <!-- Disease detections -->
            <div v-if="activeItem.result.disease.detections.length > 0">
              <div class="text-[10px] text-[#EF4444] font-mono uppercase tracking-wider mb-2">病害检测</div>
              <div class="space-y-2">
                <div
                  v-for="(det, i) in activeItem.result.disease.detections"
                  :key="'d-' + i"
                  class="flex items-center gap-3 px-3 py-2 rounded-lg border"
                  :class="getSeverityBg(det.confidence)"
                >
                  <div class="w-2 h-2 rounded-full shrink-0" :class="det.confidence >= 0.8 ? 'bg-[#EF4444]' : det.confidence >= 0.6 ? 'bg-[#FF6A00]' : 'bg-[#4ADE80]'" />
                  <div class="flex-1 min-w-0">
                    <div class="text-sm text-white font-medium truncate">{{ det.name_cn }}</div>
                    <div class="text-[10px] text-slate-500 font-mono">{{ det.class_name }}</div>
                  </div>
                  <div class="text-right shrink-0">
                    <div class="text-sm font-mono font-bold" :class="getSeverityColor(det.confidence)">{{ (det.confidence * 100).toFixed(1) }}%</div>
                    <div class="text-[10px] text-slate-600 font-mono">{{ det.bbox.width }}x{{ det.bbox.height }}</div>
                  </div>
                  <button
                    v-if="detectionMode === 'workorder'"
                    class="shrink-0 px-2 py-1 rounded-md text-[10px] font-medium border transition-all hover:scale-105"
                    :class="'bg-[#FF6A00]/10 border-[#FF6A00]/20 text-[#FF6A00] hover:bg-[#FF6A00]/20'"
                    @click="openWorkOrderModal(det, i, activeIndex, 'disease')"
                  >
                    生成工单
                  </button>
                </div>
              </div>
            </div>

            <!-- Pest detections -->
            <div v-if="activeItem.result.pest.detections.length > 0">
              <div class="text-[10px] text-[#FF6A00] font-mono uppercase tracking-wider mb-2">虫害检测</div>
              <div class="space-y-2">
                <div
                  v-for="(det, i) in activeItem.result.pest.detections"
                  :key="'p-' + i"
                  class="flex items-center gap-3 px-3 py-2 rounded-lg border"
                  :class="getSeverityBg(det.confidence)"
                >
                  <div class="w-2 h-2 rounded-full shrink-0" :class="det.confidence >= 0.8 ? 'bg-[#EF4444]' : det.confidence >= 0.6 ? 'bg-[#FF6A00]' : 'bg-[#4ADE80]'" />
                  <div class="flex-1 min-w-0">
                    <div class="text-sm text-white font-medium truncate">{{ det.name_cn }}</div>
                    <div class="text-[10px] text-slate-500 font-mono">{{ det.class_name }}</div>
                  </div>
                  <div class="text-right shrink-0">
                    <div class="text-sm font-mono font-bold" :class="getSeverityColor(det.confidence)">{{ (det.confidence * 100).toFixed(1) }}%</div>
                    <div class="text-[10px] text-slate-600 font-mono">{{ det.bbox.width }}x{{ det.bbox.height }}</div>
                  </div>
                  <button
                    v-if="detectionMode === 'workorder'"
                    class="shrink-0 px-2 py-1 rounded-md text-[10px] font-medium border transition-all hover:scale-105"
                    :class="'bg-[#FF6A00]/10 border-[#FF6A00]/20 text-[#FF6A00] hover:bg-[#FF6A00]/20'"
                    @click="openWorkOrderModal(det, i, activeIndex, 'pest')"
                  >
                    生成工单
                  </button>
                </div>
              </div>
            </div>

            <!-- No detection -->
            <div v-if="activeItem.result.disease.count === 0 && activeItem.result.pest.count === 0" class="py-6 text-center">
              <svg class="w-12 h-12 mx-auto mb-3 text-[#4ADE80]" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
              </svg>
              <p class="text-sm text-[#4ADE80] font-medium">未检测到病虫害</p>
              <p class="text-[10px] text-slate-500 mt-1">作物状态健康</p>
            </div>

            <!-- Timing -->
            <div class="text-[10px] text-slate-600 font-mono text-right">
              推理耗时: {{ activeItem.result.total_elapsed_ms.toFixed(1) }}ms
              (病害 {{ activeItem.result.disease.elapsed_ms.toFixed(1) }}ms + 虫害 {{ activeItem.result.pest.elapsed_ms.toFixed(1) }}ms)
            </div>
          </template>

          <!-- Batch navigation -->
          <div v-if="isBatch" class="flex gap-2 pt-2 border-t border-white/5">
            <button
              class="flex-1 py-2 rounded-lg text-xs transition-all"
              :class="activeIndex > 0
                ? 'bg-white/5 text-white hover:bg-white/10'
                : 'bg-white/[0.02] text-slate-600 cursor-not-allowed'"
              :disabled="activeIndex <= 0"
              @click="activeIndex--"
            >
              上一张
            </button>
            <button
              class="flex-1 py-2 rounded-lg text-xs transition-all"
              :class="activeIndex < files.length - 1
                ? 'bg-white/5 text-white hover:bg-white/10'
                : 'bg-white/[0.02] text-slate-600 cursor-not-allowed'"
              :disabled="activeIndex >= files.length - 1"
              @click="activeIndex++"
            >
              下一张
            </button>
          </div>
        </div>
      </GlassCard>
    </div>
  </div>

  <!-- Camera Modal -->
  <Teleport to="body">
    <div
      v-if="showCamera"
      class="fixed inset-0 z-50 flex flex-col items-center justify-center bg-black/90 backdrop-blur-sm"
    >
      <!-- Close button -->
      <button
        class="absolute top-4 right-4 z-10 w-10 h-10 rounded-full bg-white/10 border border-white/20 flex items-center justify-center text-white hover:bg-white/20 transition-colors"
        @click="closeCamera"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>

      <!-- Camera error -->
      <div v-if="cameraError" class="absolute top-16 left-1/2 -translate-x-1/2 px-4 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-sm max-w-md text-center">
        {{ cameraError }}
      </div>

      <!-- Video preview -->
      <video
        ref="videoRef"
        autoplay
        playsinline
        muted
        class="max-h-[75vh] max-w-[90vw] rounded-2xl shadow-2xl object-contain bg-black"
      />

      <!-- Capture button -->
      <div class="mt-6 flex items-center gap-4">
        <button
          class="w-16 h-16 rounded-full bg-white/10 border-4 border-white/40 hover:border-white/60 hover:bg-white/20 flex items-center justify-center transition-all active:scale-90"
          @click="capturePhoto"
        >
          <div class="w-12 h-12 rounded-full bg-white" />
        </button>
      </div>
      <p class="mt-3 text-xs text-slate-500">点击圆形按钮拍照</p>

      <!-- Hidden canvas for capture -->
      <canvas ref="canvasRef" class="hidden" />
    </div>
  </Teleport>

  <!-- Enlarged image overlay -->
  <Teleport to="body">
    <div
      v-if="showEnlarged && activeItem?.annotatedUrl"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm"
      @mousedown.self="showEnlarged = false"
    >
      <div class="relative">
        <img
          :src="activeItem.annotatedUrl"
          class="max-h-[90vh] max-w-[90vw] object-contain rounded-lg shadow-2xl"
        />
        <button
          class="absolute -top-3 -right-3 w-8 h-8 rounded-full bg-white/10 border border-white/20 flex items-center justify-center text-white hover:bg-white/20 transition-colors"
          @click="showEnlarged = false"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Work Order Creation Modal -->
    <div
      v-if="showWorkOrderModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      @mousedown.self="showWorkOrderModal = false"
    >
      <div class="w-full max-w-lg mx-4 rounded-2xl bg-[#0F1420] border border-white/10 shadow-2xl overflow-hidden">
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-white/10">
          <div>
            <h3 class="text-sm font-bold text-white">创建工单</h3>
            <p class="text-[10px] text-slate-500 mt-0.5">
              置信度 {{ (workOrderForm.confidence * 100).toFixed(1) }}% —
              {{ workOrderForm.confidence > 0.6 ? '高置信度，推荐分配给基层员工' : '低置信度，推荐分配给专家' }}
            </p>
          </div>
          <button
            class="w-7 h-7 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-white transition-colors"
            @click="showWorkOrderModal = false"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>

        <!-- Success state -->
        <div v-if="workOrderSuccess" class="px-5 py-10 text-center">
          <div class="w-12 h-12 mx-auto mb-3 rounded-full bg-[#4ADE80]/10 border border-[#4ADE80]/20 flex items-center justify-center">
            <svg class="w-6 h-6 text-[#4ADE80]" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M4.5 12.75l6 6 9-13.5" /></svg>
          </div>
          <p class="text-sm text-white font-medium">工单创建成功</p>
        </div>

        <!-- Form -->
        <div v-else class="px-5 py-4 space-y-4">
          <!-- Title -->
          <div>
            <label class="block text-[10px] text-slate-500 font-mono uppercase tracking-wider mb-1.5">工单标题</label>
            <input
              v-model="workOrderForm.title"
              class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-[#FF6A00]/50 transition-colors"
            />
          </div>

          <!-- Severity + Type row -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-[10px] text-slate-500 font-mono uppercase tracking-wider mb-1.5">严重程度</label>
              <select
                v-model="workOrderForm.severity"
                class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm text-white focus:outline-none focus:border-[#FF6A00]/50 transition-colors appearance-none"
              >
                <option value="LOW" class="bg-[#0F1420]">LOW</option>
                <option value="MEDIUM" class="bg-[#0F1420]">MEDIUM</option>
                <option value="HIGH" class="bg-[#0F1420]">HIGH</option>
                <option value="CRITICAL" class="bg-[#0F1420]">CRITICAL</option>
              </select>
            </div>
            <div>
              <label class="block text-[10px] text-slate-500 font-mono uppercase tracking-wider mb-1.5">病虫害名称</label>
              <input
                v-model="workOrderForm.pestName"
                readonly
                class="w-full px-3 py-2 rounded-lg bg-white/[0.03] border border-white/10 text-sm text-slate-400 cursor-not-allowed"
              />
            </div>
          </div>

          <!-- Assignee -->
          <div>
            <label class="block text-[10px] text-slate-500 font-mono uppercase tracking-wider mb-1.5">
              指派负责人
            </label>
            <select
              v-model="workOrderForm.assignedTo"
              class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm text-white focus:outline-none focus:border-[#FF6A00]/50 transition-colors appearance-none"
            >
              <option value="auto" class="bg-[#0F1420]">
                自动分配 ({{ recommendedRoleLabel(workOrderForm.confidence) }})
              </option>
              <option
                v-for="user in assignableUsers"
                :key="user.id"
                :value="user.id"
                class="bg-[#0F1420]"
              >
                {{ user.name }} ({{ user.role === 'EXPERT' ? '专家' : user.role === 'STAFF' ? '基层员工' : '管理员' }})
              </option>
            </select>
          </div>

          <!-- Confidence display -->
          <div class="flex items-center gap-2 px-3 py-2 rounded-lg bg-white/[0.03] border border-white/[0.06]">
            <div class="w-2 h-2 rounded-full" :class="workOrderForm.confidence >= 0.8 ? 'bg-[#EF4444]' : workOrderForm.confidence >= 0.6 ? 'bg-[#FF6A00]' : 'bg-[#4ADE80]'" />
            <span class="text-[10px] text-slate-500 font-mono">检测置信度</span>
            <span class="text-sm font-mono font-bold ml-auto" :class="getSeverityColor(workOrderForm.confidence)">
              {{ (workOrderForm.confidence * 100).toFixed(1) }}%
            </span>
          </div>

          <!-- Submit -->
          <button
            class="w-full py-3 rounded-xl text-sm font-bold transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            :class="workOrderSubmitting
              ? 'bg-white/5 text-slate-500 border border-white/5'
              : 'bg-gradient-to-r from-[#FF6A00] to-[#FFB300] text-[#0B0F19] hover:shadow-lg hover:shadow-[#FF6A00]/20 hover:scale-[1.01]'"
            :disabled="workOrderSubmitting"
            @click="submitWorkOrder"
          >
            {{ workOrderSubmitting ? '创建中...' : '确认创建工单' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>


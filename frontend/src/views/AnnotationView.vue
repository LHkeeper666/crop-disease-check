<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlowButton from '../components/GlowButton.vue'
import {
  saveAnnotation as apiSave,
  getAnnotationByWorkOrder,
  getClassOptions,
  exportYoloTxt,
  type AnnotationBoxDTO,
  type ClassOptionVO,
} from '../api/annotation'
import { fetchWorkOrderDetail } from '../api/workorder'

const route = useRoute()
const router = useRouter()
const workOrderId = computed(() => Number(route.params.id))

// ========== 状态 ==========
const loading = ref(true)
const saving = ref(false)
const saveSuccess = ref(false)
const error = ref('')
const workOrderTitle = ref('')
const workOrderType = ref<'disease' | 'pest'>('disease')
const imageUrl = ref('')
const annotationId = ref<number | null>(null)

// 标注框（归一化 YOLO 坐标）
const boxes = ref<AnnotationBoxDTO[]>([])
const selectedBoxIndex = ref(-1)

// 类别
const classOptions = ref<ClassOptionVO[]>([])
const classSearch = ref('')
const selectedClass = ref<ClassOptionVO | null>(null)

// AI 检测框参考
const aiBoxes = ref<Array<{ classId: number; nameCn: string; x: number; y: number; width: number; height: number }>>([])
const showAiBoxes = ref(true)

// Canvas
const canvasRef = ref<HTMLCanvasElement>()
const canvasContainerRef = ref<HTMLDivElement>()
const image = ref<HTMLImageElement | null>(null)
const imgScale = ref(1)
const imgOffsetX = ref(0)
const imgOffsetY = ref(0)

// 绘制状态
const drawing = ref(false)
const drawStart = ref({ x: 0, y: 0 })
const drawCurrent = ref({ x: 0, y: 0 })

// 拖拽/缩放状态
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const dragBoxStart = ref({ x: 0, y: 0, w: 0, h: 0 })
const resizing = ref(false)
const resizeHandle = ref('') // 'tl','tr','bl','br'

const HANDLE_SIZE = 8

// ========== 类别过滤 ==========
const filteredClassOptions = computed(() => {
  if (!classSearch.value) return classOptions.value
  const q = classSearch.value.toLowerCase()
  return classOptions.value.filter(o =>
    o.className.toLowerCase().includes(q) || o.nameCn.toLowerCase().includes(q)
  )
})

// ========== 坐标转换 ==========
function normToPixel(nx: number, ny: number): { x: number; y: number } {
  if (!image.value) return { x: 0, y: 0 }
  return {
    x: imgOffsetX.value + nx * image.value.naturalWidth * imgScale.value,
    y: imgOffsetY.value + ny * image.value.naturalHeight * imgScale.value,
  }
}

function pixelToNorm(px: number, py: number): { x: number; y: number } {
  if (!image.value) return { x: 0, y: 0 }
  return {
    x: (px - imgOffsetX.value) / (image.value.naturalWidth * imgScale.value),
    y: (py - imgOffsetY.value) / (image.value.naturalHeight * imgScale.value),
  }
}

function boxToPixelRect(box: { x: number; y: number; width: number; height: number }) {
  // YOLO: center_x, center_y, width, height
  const tl = normToPixel(box.x - box.width / 2, box.y - box.height / 2)
  const br = normToPixel(box.x + box.width / 2, box.y + box.height / 2)
  return { x: tl.x, y: tl.y, w: br.x - tl.x, h: br.y - tl.y }
}

// ========== 渲染 ==========
function render() {
  const canvas = canvasRef.value
  const img = image.value
  if (!canvas || !img) return

  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  // 画图片
  ctx.drawImage(img, imgOffsetX.value, imgOffsetY.value,
    img.naturalWidth * imgScale.value, img.naturalHeight * imgScale.value)

  // 画 AI 检测框（半透明蓝色）
  if (showAiBoxes.value) {
    ctx.save()
    ctx.strokeStyle = 'rgba(96, 165, 250, 0.7)'
    ctx.fillStyle = 'rgba(96, 165, 250, 0.1)'
    ctx.lineWidth = 1.5
    ctx.setLineDash([6, 3])
    for (const ab of aiBoxes.value) {
      const r = boxToPixelRect(ab)
      ctx.fillRect(r.x, r.y, r.w, r.h)
      ctx.strokeRect(r.x, r.y, r.w, r.h)
      ctx.fillStyle = 'rgba(96, 165, 250, 0.85)'
      ctx.font = '11px monospace'
      ctx.fillText(ab.nameCn, r.x + 3, r.y - 4)
      ctx.fillStyle = 'rgba(96, 165, 250, 0.1)'
    }
    ctx.restore()
  }

  // 画专家标注框（绿色）
  ctx.save()
  for (let i = 0; i < boxes.value.length; i++) {
    const box = boxes.value[i]
    const r = boxToPixelRect(box)
    const isSelected = i === selectedBoxIndex.value

    ctx.strokeStyle = isSelected ? '#22d3ee' : '#4ade80'
    ctx.fillStyle = isSelected ? 'rgba(34, 211, 238, 0.12)' : 'rgba(74, 222, 128, 0.08)'
    ctx.lineWidth = isSelected ? 2.5 : 2
    ctx.setLineDash([])
    ctx.fillRect(r.x, r.y, r.w, r.h)
    ctx.strokeRect(r.x, r.y, r.w, r.h)

    // 标签
    ctx.fillStyle = isSelected ? '#22d3ee' : '#4ade80'
    ctx.font = 'bold 12px monospace'
    ctx.fillText(box.nameCn, r.x + 3, r.y - 5)

    // 选中时画角点手柄
    if (isSelected) {
      const handles = getHandles(r)
      ctx.fillStyle = '#22d3ee'
      for (const h of handles) {
        ctx.fillRect(h.x - HANDLE_SIZE / 2, h.y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE)
      }
    }
  }
  ctx.restore()

  // 画正在绘制的框
  if (drawing.value) {
    ctx.save()
    ctx.strokeStyle = '#facc15'
    ctx.fillStyle = 'rgba(250, 204, 21, 0.1)'
    ctx.lineWidth = 2
    ctx.setLineDash([5, 3])
    const x = Math.min(drawStart.value.x, drawCurrent.value.x)
    const y = Math.min(drawStart.value.y, drawCurrent.value.y)
    const w = Math.abs(drawCurrent.value.x - drawStart.value.x)
    const h = Math.abs(drawCurrent.value.y - drawStart.value.y)
    ctx.fillRect(x, y, w, h)
    ctx.strokeRect(x, y, w, h)
    ctx.restore()
  }
}

function getHandles(r: { x: number; y: number; w: number; h: number }) {
  return [
    { x: r.x, y: r.y, key: 'tl' },
    { x: r.x + r.w, y: r.y, key: 'tr' },
    { x: r.x, y: r.y + r.h, key: 'bl' },
    { x: r.x + r.w, y: r.y + r.h, key: 'br' },
  ]
}

function hitHandle(px: number, py: number, r: { x: number; y: number; w: number; h: number }): string {
  for (const h of getHandles(r)) {
    if (Math.abs(px - h.x) <= HANDLE_SIZE && Math.abs(py - h.y) <= HANDLE_SIZE) {
      return h.key
    }
  }
  return ''
}

function hitBox(px: number, py: number): number {
  for (let i = boxes.value.length - 1; i >= 0; i--) {
    const r = boxToPixelRect(boxes.value[i])
    if (px >= r.x && px <= r.x + r.w && py >= r.y && py <= r.y + r.h) {
      return i
    }
  }
  return -1
}

// ========== 鼠标事件 ==========
function onMouseDown(e: MouseEvent) {
  const rect = canvasRef.value!.getBoundingClientRect()
  const px = e.clientX - rect.left
  const py = e.clientY - rect.top

  // 检查是否点中选中框的角点
  if (selectedBoxIndex.value >= 0) {
    const r = boxToPixelRect(boxes.value[selectedBoxIndex.value])
    const handle = hitHandle(px, py, r)
    if (handle) {
      resizing.value = true
      resizeHandle.value = handle
      dragStart.value = { x: px, y: py }
      dragBoxStart.value = { ...boxes.value[selectedBoxIndex.value] } as any
      return
    }
  }

  // 检查是否点中已有框
  const hitIdx = hitBox(px, py)
  if (hitIdx >= 0 && !selectedClass.value) {
    // 没选类别时，点击框为选中
    selectedBoxIndex.value = hitIdx
    dragging.value = true
    dragStart.value = { x: px, y: py }
    dragBoxStart.value = { ...boxes.value[hitIdx] } as any
    render()
    return
  }

  if (hitIdx >= 0 && selectedClass.value) {
    // 选了类别时，点击框也选中
    selectedBoxIndex.value = hitIdx
    dragging.value = true
    dragStart.value = { x: px, y: py }
    dragBoxStart.value = { ...boxes.value[hitIdx] } as any
    render()
    return
  }

  // 如果没选类别，不能画新框
  if (!selectedClass.value) {
    selectedBoxIndex.value = -1
    render()
    return
  }

  // 开始画新框
  drawing.value = true
  drawStart.value = { x: px, y: py }
  drawCurrent.value = { x: px, y: py }
  selectedBoxIndex.value = -1
}

function onMouseMove(e: MouseEvent) {
  const rect = canvasRef.value!.getBoundingClientRect()
  const px = e.clientX - rect.left
  const py = e.clientY - rect.top

  if (drawing.value) {
    drawCurrent.value = { x: px, y: py }
    render()
    return
  }

  if (resizing.value && selectedBoxIndex.value >= 0) {
    const box = boxes.value[selectedBoxIndex.value]
    const orig = dragBoxStart.value
    const dx = (px - dragStart.value.x) / (image.value!.naturalWidth * imgScale.value)
    const dy = (py - dragStart.value.y) / (image.value!.naturalHeight * imgScale.value)

    let newX = orig.x, newY = orig.y, newW = orig.width, newH = orig.height

    if (resizeHandle.value === 'tl') {
      newX = orig.x + dx / 2
      newY = orig.y + dy / 2
      newW = orig.width - dx
      newH = orig.height - dy
    } else if (resizeHandle.value === 'tr') {
      newX = orig.x + dx / 2
      newY = orig.y + dy / 2
      newW = orig.width + dx
      newH = orig.height - dy
    } else if (resizeHandle.value === 'bl') {
      newX = orig.x + dx / 2
      newY = orig.y + dy / 2
      newW = orig.width - dx
      newH = orig.height + dy
    } else if (resizeHandle.value === 'br') {
      newX = orig.x + dx / 2
      newY = orig.y + dy / 2
      newW = orig.width + dx
      newH = orig.height + dy
    }

    if (newW > 0.005 && newH > 0.005) {
      box.x = newX
      box.y = newY
      box.width = newW
      box.height = newH
      render()
    }
    return
  }

  if (dragging.value && selectedBoxIndex.value >= 0) {
    const box = boxes.value[selectedBoxIndex.value]
    const dx = (px - dragStart.value.x) / (image.value!.naturalWidth * imgScale.value)
    const dy = (py - dragStart.value.y) / (image.value!.naturalHeight * imgScale.value)
    box.x = dragBoxStart.value.x + dx
    box.y = dragBoxStart.value.y + dy
    render()
    return
  }

  // 鼠标样式
  if (selectedBoxIndex.value >= 0) {
    const r = boxToPixelRect(boxes.value[selectedBoxIndex.value])
    const handle = hitHandle(px, py, r)
    if (handle) {
      canvasRef.value!.style.cursor = (handle === 'tl' || handle === 'br') ? 'nwse-resize' : 'nesw-resize'
      return
    }
  }
  const hitIdx = hitBox(px, py)
  canvasRef.value!.style.cursor = hitIdx >= 0 ? 'move' : (selectedClass.value ? 'crosshair' : 'default')
}

function onMouseUp(e: MouseEvent) {
  const rect = canvasRef.value!.getBoundingClientRect()
  const px = e.clientX - rect.left
  const py = e.clientY - rect.top

  if (drawing.value) {
    drawing.value = false
    const x = Math.min(drawStart.value.x, px)
    const y = Math.min(drawStart.value.y, py)
    const w = Math.abs(px - drawStart.value.x)
    const h = Math.abs(py - drawStart.value.y)

    // 最小尺寸过滤
    if (w > 5 && h > 5 && selectedClass.value) {
      const tl = pixelToNorm(x, y)
      const br = pixelToNorm(x + w, y + h)
      const cx = (tl.x + br.x) / 2
      const cy = (tl.y + br.y) / 2
      const nw = br.x - tl.x
      const nh = br.y - tl.y

      boxes.value.push({
        classId: selectedClass.value.id,
        className: selectedClass.value.className,
        nameCn: selectedClass.value.nameCn,
        x: cx, y: cy, width: nw, height: nh,
      })
      selectedBoxIndex.value = boxes.value.length - 1
    }
    render()
    return
  }

  dragging.value = false
  resizing.value = false
  render()
}

// ========== 键盘事件 ==========
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (selectedBoxIndex.value >= 0 && !(e.target as HTMLElement).matches('input,textarea')) {
      deleteBox(selectedBoxIndex.value)
    }
  }
  if (e.key === 'Escape') {
    selectedBoxIndex.value = -1
    selectedClass.value = null
    render()
  }
}

// ========== 操作 ==========
function selectClass(opt: ClassOptionVO) {
  selectedClass.value = opt
}

function selectBox(idx: number) {
  selectedBoxIndex.value = idx
  render()
}

function deleteBox(idx: number) {
  boxes.value.splice(idx, 1)
  if (selectedBoxIndex.value >= boxes.value.length) {
    selectedBoxIndex.value = boxes.value.length - 1
  }
  render()
}

// ========== 保存 ==========
async function saveAnnotations() {
  if (!boxes.value.length) {
    error.value = '请至少标注一个检测框'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const id = await apiSave({
      workOrderId: workOrderId.value,
      imageUrl: imageUrl.value,
      pipeline: workOrderType.value,
      boxes: boxes.value,
    })
    annotationId.value = id
    saveSuccess.value = true
    // 保存成功后自动返回工单详情
    setTimeout(() => {
      router.push({ name: 'WorkOrders', query: { open: workOrderId.value } })
    }, 800)
  } catch (e: any) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

// ========== 导出 ==========
async function handleExportYolo() {
  if (!annotationId.value) return
  try {
    const txt = await exportYoloTxt(annotationId.value)
    const blob = new Blob([txt], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `annotation_${workOrderId.value}.txt`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    error.value = e.message || '导出失败'
  }
}

// ========== 画布适配 ==========
function fitCanvas() {
  const canvas = canvasRef.value
  const container = canvasContainerRef.value
  const img = image.value
  if (!canvas || !container || !img) return

  canvas.width = container.clientWidth
  canvas.height = container.clientHeight

  const scaleX = canvas.width / img.naturalWidth
  const scaleY = canvas.height / img.naturalHeight
  imgScale.value = Math.min(scaleX, scaleY) * 0.92
  imgOffsetX.value = (canvas.width - img.naturalWidth * imgScale.value) / 2
  imgOffsetY.value = (canvas.height - img.naturalHeight * imgScale.value) / 2

  render()
}

// ========== 初始化 ==========
onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', fitCanvas)

  try {
    // 加载工单详情
    const detail = await fetchWorkOrderDetail(workOrderId.value.toString())
    workOrderTitle.value = detail.title
    workOrderType.value = (detail.type as 'disease' | 'pest') || 'disease'
    imageUrl.value = detail.originalImageUrl || detail.imageUrl || ''

    // 加载类别列表
    classOptions.value = await getClassOptions(workOrderType.value)

    // 加载已有标注
    try {
      const existing = await getAnnotationByWorkOrder(workOrderId.value)
      if (existing) {
        annotationId.value = existing.id
        boxes.value = existing.boxes.map(b => ({
          classId: b.classId,
          className: b.className,
          nameCn: b.nameCn,
          x: b.x, y: b.y, width: b.width, height: b.height,
        }))
      }
    } catch { /* 无已有标注 */ }

    // 加载 AI 检测框作为参考
    if (detail.inferenceId) {
      try {
        const { request: req } = await import('../api/request')
        const inf = await req<any>(`/api/inference/${detail.inferenceId}`)
        if (inf?.detections) {
          const dets = typeof inf.detections === 'string' ? JSON.parse(inf.detections) : inf.detections
          const imgEl = new Image()
          imgEl.crossOrigin = 'anonymous'
          imgEl.src = imageUrl.value
          await new Promise<void>((resolve) => { imgEl.onload = () => resolve(); imgEl.onerror = () => resolve() })
          const iw = imgEl.naturalWidth
          const ih = imgEl.naturalHeight
          if (iw > 0 && ih > 0) {
            aiBoxes.value = dets.map((d: any) => ({
              classId: d.class_id ?? d.classId,
              nameCn: d.name_cn ?? d.nameCn ?? d.class_name ?? '',
              x: (d.bbox.x + d.bbox.width / 2) / iw,
              y: (d.bbox.y + d.bbox.height / 2) / ih,
              width: d.bbox.width / iw,
              height: d.bbox.height / ih,
            }))
          }
        }
      } catch { /* 无 AI 检测框 */ }
    }

    // 加载图片到 canvas
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      image.value = img
      nextTick(() => fitCanvas())
    }
    img.onerror = () => {
      error.value = '图片加载失败，请检查 MinIO CORS 配置'
    }
    img.src = imageUrl.value
  } catch (e: any) {
    error.value = e.message || '加载工单信息失败'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', fitCanvas)
})

function goBack() {
  router.push({ name: 'WorkOrders' })
}
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-[#0B0F19]">
    <!-- 顶部工具栏 -->
    <div class="shrink-0 flex items-center justify-between px-4 py-3 border-b border-white/10 bg-[#0F1420]">
      <div class="flex items-center gap-3">
        <button
          class="w-8 h-8 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-white transition-colors"
          @click="goBack"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
          </svg>
        </button>
        <div>
          <h1 class="text-lg font-bold text-white">专家标注</h1>
          <p class="text-[10px] text-slate-500 font-mono">EXPERT ANNOTATION · {{ workOrderTitle }}</p>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-slate-400 font-mono">
          已标注 <span class="text-white font-bold">{{ boxes.length }}</span> 个框
        </span>
        <span v-if="aiBoxes.length" class="text-xs text-blue-400 font-mono">
          AI 参考 {{ aiBoxes.length }} 框
        </span>
        <button
          v-if="annotationId"
          class="px-3 py-1.5 rounded-lg text-xs bg-white/5 border border-white/10 text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
          @click="handleExportYolo"
        >
          导出 YOLO
        </button>
        <GlowButton label="保存标注" :loading="saving" @click="saveAnnotations" />
      </div>
    </div>

    <!-- 错误/成功提示 -->
    <div v-if="error" class="shrink-0 px-4 py-2 bg-[#EF4444]/10 border-b border-[#EF4444]/20 text-[#EF4444] text-xs flex items-center gap-2">
      <svg class="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
      {{ error }}
      <button class="ml-auto text-slate-500 hover:text-white" @click="error = ''">×</button>
    </div>
    <div v-if="saveSuccess" class="shrink-0 px-4 py-2 bg-[#4ADE80]/10 border-b border-[#4ADE80]/20 text-[#4ADE80] text-xs">
      标注保存成功！
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-slate-500 text-sm font-mono">加载中...</div>
    </div>

    <!-- 主体区域 -->
    <div v-else class="flex-1 flex min-h-0">
      <!-- Canvas 画布区域 -->
      <div class="flex-1 relative overflow-hidden" ref="canvasContainerRef">
        <canvas
          ref="canvasRef"
          class="w-full h-full"
          @mousedown="onMouseDown"
          @mousemove="onMouseMove"
          @mouseup="onMouseUp"
          @mouseleave="onMouseUp"
        />

        <!-- AI 检测框开关 -->
        <div class="absolute top-3 left-3 flex items-center gap-2 px-3 py-1.5 rounded-lg bg-black/60 backdrop-blur">
          <label class="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
            <input type="checkbox" v-model="showAiBoxes" class="accent-blue-400" />
            显示 AI 检测框
          </label>
        </div>

        <!-- 快捷键提示 -->
        <div class="absolute bottom-3 left-3 px-3 py-1.5 rounded-lg bg-black/60 backdrop-blur text-[10px] text-slate-500 font-mono">
          Delete 删除选中框 · Esc 取消选择 · 拖拽绘制新框
        </div>
      </div>

      <!-- 右侧边栏 -->
      <div class="w-72 border-l border-white/10 flex flex-col bg-[#0F1420]">
        <!-- 类别选择 -->
        <div class="p-3 border-b border-white/10">
          <div class="text-[10px] text-slate-500 font-mono uppercase tracking-wider mb-2">
            选择类别后在图片上拖拽画框
          </div>
          <input
            v-model="classSearch"
            type="text"
            placeholder="搜索类别名称..."
            class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-blue-400/50 transition-colors"
          />
          <div class="max-h-40 overflow-y-auto mt-2 space-y-0.5">
            <div
              v-for="opt in filteredClassOptions"
              :key="opt.id"
              class="px-2 py-1.5 rounded-md text-xs cursor-pointer transition-colors"
              :class="selectedClass?.id === opt.id
                ? 'bg-blue-400/15 text-blue-400 border border-blue-400/20'
                : 'text-slate-300 hover:bg-white/5'"
              @click="selectClass(opt)"
            >
              <span class="font-medium">{{ opt.nameCn }}</span>
              <span class="text-slate-600 ml-1 text-[10px]">{{ opt.className }}</span>
            </div>
          </div>
        </div>

        <!-- 当前选中类别 -->
        <div class="px-3 py-2 border-b border-white/10">
          <div v-if="selectedClass" class="flex items-center gap-2">
            <div class="w-2 h-2 rounded-full bg-blue-400" />
            <span class="text-xs text-blue-400 font-medium">当前: {{ selectedClass.nameCn }}</span>
            <button class="ml-auto text-[10px] text-slate-500 hover:text-white" @click="selectedClass = null">清除</button>
          </div>
          <div v-else class="text-[10px] text-slate-600">请先选择类别再画框</div>
        </div>

        <!-- 标注框列表 -->
        <div class="flex-1 overflow-y-auto p-3 space-y-1.5">
          <div class="text-[10px] text-slate-500 font-mono uppercase tracking-wider mb-2">
            标注框列表
          </div>
          <div v-if="boxes.length === 0" class="text-xs text-slate-600 text-center py-4">
            暂无标注框
          </div>
          <div
            v-for="(box, idx) in boxes"
            :key="idx"
            class="rounded-lg p-2 cursor-pointer transition-all border"
            :class="selectedBoxIndex === idx
              ? 'bg-cyan-500/10 border-cyan-500/30'
              : 'bg-white/[0.03] border-white/[0.06] hover:border-white/15'"
            @click="selectBox(idx)"
          >
            <div class="flex items-center justify-between">
              <div>
                <span class="text-xs text-white font-medium">{{ box.nameCn }}</span>
                <span class="text-[10px] text-slate-600 ml-1">{{ box.className }}</span>
              </div>
              <button
                class="w-5 h-5 rounded flex items-center justify-center text-slate-600 hover:text-[#EF4444] hover:bg-[#EF4444]/10 transition-colors"
                @click.stop="deleteBox(idx)"
              >
                <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>
            <div class="text-[10px] text-slate-600 font-mono mt-1">
              x={{ box.x.toFixed(4) }} y={{ box.y.toFixed(4) }} w={{ box.width.toFixed(4) }} h={{ box.height.toFixed(4) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

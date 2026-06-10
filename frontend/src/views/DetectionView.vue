<script setup lang="ts">
import { ref } from 'vue'
import GlassCard from '../components/GlassCard.vue'

interface Detection {
  class_name: string
  name_cn: string
  confidence: number
  bbox: { x: number; y: number; width: number; height: number }
}

interface DetectionResult {
  disease: { detections: Detection[]; count: number; elapsed_ms: number }
  pest: { detections: Detection[]; count: number; elapsed_ms: number }
  annotated_image: string | null
  total_elapsed_ms: number
}

const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const isUploading = ref(false)
const result = ref<DetectionResult | null>(null)
const error = ref('')
const annotatedPreviewUrl = ref('')
const confidenceThreshold = ref(0.5)
const dragOver = ref(false)
const fileInputRef = ref<HTMLInputElement>()

function onFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  setFile(file)
}

function setFile(file: File) {
  if (!file.type.startsWith('image/')) {
    error.value = '请选择 JPG 或 PNG 格式的图片'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    error.value = '图片大小不能超过 10MB'
    return
  }
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  result.value = null
  error.value = ''
  annotatedPreviewUrl.value = ''
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) setFile(file)
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragOver.value = true
}

function onDragLeave() {
  dragOver.value = false
}

function clearFile() {
  selectedFile.value = null
  previewUrl.value = ''
  result.value = null
  error.value = ''
  annotatedPreviewUrl.value = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

async function handleDetect() {
  if (!selectedFile.value) return
  isUploading.value = true
  error.value = ''
  result.value = null
  annotatedPreviewUrl.value = ''

  try {
    const reader = new FileReader()
    const base64 = await new Promise<string>((resolve, reject) => {
      reader.onload = () => resolve(reader.result as string)
      reader.onerror = reject
      reader.readAsDataURL(selectedFile.value!)
    })

    const base64Data = base64.split(',')[1]

    const response = await fetch('/api/v1/detect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        image: { type: 'base64', data: base64Data },
        confidence: confidenceThreshold.value,
        return_annotated_image: true,
      }),
    })

    if (!response.ok) {
      const err = await response.json().catch(() => ({}))
      throw new Error(err.message || `请求失败: ${response.statusText}`)
    }

    const data = await response.json()
    if (data.code !== 200) {
      throw new Error(data.message || '检测失败')
    }

    result.value = data.data

    if (data.data.annotated_image) {
      annotatedPreviewUrl.value = `data:image/jpeg;base64,${data.data.annotated_image}`
    }
  } catch (err: any) {
    error.value = err.message || '检测请求失败，请检查推理服务是否运行'
  } finally {
    isUploading.value = false
  }
}

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
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">图像检测</h1>
        <p class="text-xs text-slate-500 font-mono">AI-POWERED DISEASE & PEST DETECTION</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2">
          <span class="text-[10px] text-slate-500 font-mono uppercase tracking-wider">置信度阈值</span>
          <input
            v-model.number="confidenceThreshold"
            type="range"
            min="0.1"
            max="0.9"
            step="0.05"
            class="w-24 accent-[#FF6A00]"
          />
          <span class="text-xs text-white font-mono w-10">{{ confidenceThreshold.toFixed(2) }}</span>
        </div>
      </div>
    </div>

    <div class="flex-1 min-h-0 grid grid-cols-2 gap-4">
      <!-- Left: Upload area -->
      <GlassCard class="flex flex-col">
        <div class="text-xs text-slate-400 tracking-wider mb-3 shrink-0">图片上传</div>

        <!-- Drop zone -->
        <div
          class="flex-1 min-h-0 rounded-xl border-2 border-dashed transition-all duration-200 flex flex-col items-center justify-center cursor-pointer"
          :class="dragOver
            ? 'border-[#FF6A00]/50 bg-[#FF6A00]/5'
            : previewUrl
              ? 'border-white/10 bg-white/[0.02]'
              : 'border-white/10 bg-white/[0.02] hover:border-white/20 hover:bg-white/[0.04]'"
          @drop.prevent="onDrop"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @click="!previewUrl && fileInputRef?.click()"
        >
          <!-- Preview -->
          <div v-if="previewUrl" class="relative w-full h-full p-2">
            <img :src="previewUrl" class="w-full h-full object-contain rounded-lg" />
            <button
              class="absolute top-3 right-3 w-7 h-7 rounded-lg bg-black/60 backdrop-blur flex items-center justify-center text-slate-400 hover:text-white transition-colors"
              @click.stop="clearFile"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
          </div>

          <!-- Empty state -->
          <div v-else class="text-center px-4">
            <svg class="w-12 h-12 mx-auto mb-3 text-slate-600" fill="none" stroke="currentColor" stroke-width="1" viewBox="0 0 24 24">
              <path d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21z" />
            </svg>
            <p class="text-sm text-slate-400 mb-1">拖拽图片到此处或点击选择</p>
            <p class="text-[10px] text-slate-600">支持 JPG / PNG，最大 10MB</p>
          </div>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png"
          class="hidden"
          @change="onFileSelect"
        />

        <!-- Action -->
        <button
          class="mt-3 w-full py-3 rounded-xl text-sm font-bold transition-all disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
          :class="!selectedFile || isUploading
            ? 'bg-white/5 text-slate-500 border border-white/5'
            : 'bg-gradient-to-r from-[#FF6A00] to-[#FFB300] text-[#0B0F19] hover:shadow-lg hover:shadow-[#FF6A00]/20 hover:scale-[1.01]'"
          :disabled="!selectedFile || isUploading"
          @click="handleDetect"
        >
          {{ isUploading ? '正在检测...' : '开始检测' }}
        </button>
      </GlassCard>

      <!-- Right: Results -->
      <GlassCard class="flex flex-col">
        <div class="text-xs text-slate-400 tracking-wider mb-3 shrink-0">检测结果</div>

        <!-- Error -->
        <div v-if="error" class="mb-3 px-3 py-2 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444] text-xs flex items-center gap-2 shrink-0">
          <svg class="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
          {{ error }}
        </div>

        <!-- Empty state -->
        <div v-if="!result && !error && !isUploading" class="flex-1 flex flex-col items-center justify-center">
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
        <div v-if="result" class="flex-1 min-h-0 overflow-y-auto space-y-3">
          <!-- Annotated image -->
          <div v-if="annotatedPreviewUrl" class="rounded-xl overflow-hidden border border-white/10">
            <img :src="annotatedPreviewUrl" class="w-full object-contain max-h-48" />
          </div>

          <!-- Stats bar -->
          <div class="grid grid-cols-3 gap-2">
            <div class="rounded-lg bg-white/[0.03] border border-white/[0.06] p-2 text-center">
              <div class="text-lg font-bold font-mono text-white">{{ result.disease.count + result.pest.count }}</div>
              <div class="text-[10px] text-slate-500">总检出</div>
            </div>
            <div class="rounded-lg bg-[#EF4444]/5 border border-[#EF4444]/10 p-2 text-center">
              <div class="text-lg font-bold font-mono text-[#EF4444]">{{ result.disease.count }}</div>
              <div class="text-[10px] text-slate-500">病害</div>
            </div>
            <div class="rounded-lg bg-[#FF6A00]/5 border border-[#FF6A00]/10 p-2 text-center">
              <div class="text-lg font-bold font-mono text-[#FF6A00]">{{ result.pest.count }}</div>
              <div class="text-[10px] text-slate-500">虫害</div>
            </div>
          </div>

          <!-- Disease detections -->
          <div v-if="result.disease.detections.length > 0">
            <div class="text-[10px] text-[#EF4444] font-mono uppercase tracking-wider mb-2">病害检测</div>
            <div class="space-y-2">
              <div
                v-for="(det, i) in result.disease.detections"
                :key="'d-' + i"
                class="flex items-center gap-3 px-3 py-2 rounded-lg border"
                :class="getSeverityBg(det.confidence)"
              >
                <div class="w-2 h-2 rounded-full shrink-0" :class="det.confidence >= 0.8 ? 'bg-[#EF4444]' : det.confidence >= 0.6 ? 'bg-[#FF6A00]' : 'bg-[#4ADE80]'" />
                <div class="flex-1 min-w-0">
                  <div class="text-sm text-white font-medium truncate">{{ det.name_cn }}</div>
                  <div class="text-[10px] text-slate-500 font-mono">{{ det.class_name }}</div>
                </div>
                <div class="text-right">
                  <div class="text-sm font-mono font-bold" :class="getSeverityColor(det.confidence)">{{ (det.confidence * 100).toFixed(1) }}%</div>
                  <div class="text-[10px] text-slate-600 font-mono">{{ det.bbox.width }}x{{ det.bbox.height }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Pest detections -->
          <div v-if="result.pest.detections.length > 0">
            <div class="text-[10px] text-[#FF6A00] font-mono uppercase tracking-wider mb-2">虫害检测</div>
            <div class="space-y-2">
              <div
                v-for="(det, i) in result.pest.detections"
                :key="'p-' + i"
                class="flex items-center gap-3 px-3 py-2 rounded-lg border"
                :class="getSeverityBg(det.confidence)"
              >
                <div class="w-2 h-2 rounded-full shrink-0" :class="det.confidence >= 0.8 ? 'bg-[#EF4444]' : det.confidence >= 0.6 ? 'bg-[#FF6A00]' : 'bg-[#4ADE80]'" />
                <div class="flex-1 min-w-0">
                  <div class="text-sm text-white font-medium truncate">{{ det.name_cn }}</div>
                  <div class="text-[10px] text-slate-500 font-mono">{{ det.class_name }}</div>
                </div>
                <div class="text-right">
                  <div class="text-sm font-mono font-bold" :class="getSeverityColor(det.confidence)">{{ (det.confidence * 100).toFixed(1) }}%</div>
                  <div class="text-[10px] text-slate-600 font-mono">{{ det.bbox.width }}x{{ det.bbox.height }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- No detection -->
          <div v-if="result.disease.count === 0 && result.pest.count === 0" class="py-6 text-center">
            <svg class="w-12 h-12 mx-auto mb-3 text-[#4ADE80]" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
            </svg>
            <p class="text-sm text-[#4ADE80] font-medium">未检测到病虫害</p>
            <p class="text-[10px] text-slate-500 mt-1">作物状态健康</p>
          </div>

          <!-- Timing -->
          <div class="text-[10px] text-slate-600 font-mono text-right">
            推理耗时: {{ result.total_elapsed_ms.toFixed(1) }}ms
            (病害 {{ result.disease.elapsed_ms.toFixed(1) }}ms + 虫害 {{ result.pest.elapsed_ms.toFixed(1) }}ms)
          </div>
        </div>
      </GlassCard>
    </div>
  </div>
</template>

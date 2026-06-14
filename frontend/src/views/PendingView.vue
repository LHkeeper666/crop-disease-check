<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const inviteCode = ref('')
const isValidating = ref(false)
const result = ref<{ success: boolean; message: string; companyName?: string } | null>(null)

async function handleJoin() {
  if (!inviteCode.value.trim()) {
    result.value = { success: false, message: '请输入邀请码' }
    return
  }
  isValidating.value = true
  result.value = null
  const validation = await auth.validateInviteCode(inviteCode.value.trim())
  if (!validation.success) {
    result.value = validation
    isValidating.value = false
    return
  }
  const joined = await auth.joinCompany(inviteCode.value.trim())
  isValidating.value = false
  if (joined) {
    result.value = { success: true, message: `已加入 ${validation.companyName}，正在跳转...`, companyName: validation.companyName }
    setTimeout(() => router.push('/dashboard'), 1500)
  } else {
    result.value = { success: false, message: '加入失败，请重试' }
  }
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}

// === Carousel ===
interface CarouselItem {
  image: string
  link: string
  title: string
  desc: string
}

const slides: CarouselItem[] = [
  { image: '/images/marquee/农业知识.png', link: 'https://agriknow.cn/', title: '农业知识百科', desc: '探索现代农业科技前沿' },
  { image: '/images/marquee/西北工业大学.png', link: 'https://www.nwpu.edu.cn/', title: '西北工业大学', desc: '智慧农业科研合作平台' },
  { image: '/images/marquee/农业科技信息.png', link: 'https://cast.caas.cn/index.html', title: '农业科技信息', desc: '中国农业科学院权威资讯' },
]

const currentSlide = ref(0)
const isHovering = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
const progress = ref(0)
let progressTimer: ReturnType<typeof setInterval> | null = null

function nextSlide() {
  currentSlide.value = (currentSlide.value + 1) % slides.length
}

function prevSlide() {
  currentSlide.value = (currentSlide.value - 1 + slides.length) % slides.length
}

function goTo(index: number) {
  currentSlide.value = index
  restartTimer()
}

function startProgress() {
  stopProgress()
  progress.value = 0
  progressTimer = setInterval(() => {
    progress.value += 2.5
    if (progress.value >= 100) {
      progress.value = 0
    }
  }, 100)
}

function stopProgress() {
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  progress.value = 0
}

function startTimer() {
  stopTimer()
  timer = setInterval(() => {
    if (!isHovering.value) nextSlide()
  }, 4000)
  startProgress()
}

function stopTimer() {
  if (timer) { clearInterval(timer); timer = null }
  stopProgress()
}

function restartTimer() {
  stopTimer()
  startTimer()
}

onMounted(() => startTimer())
onUnmounted(() => stopTimer())
</script>

<template>
  <div class="relative w-full h-screen overflow-hidden bg-[#0B0F19]">
    <!-- ============ Full-screen background slides ============ -->
    <TransitionGroup name="bg-fade" tag="div" class="absolute inset-0">
      <div
        v-for="(slide, index) in slides"
        :key="slide.link"
        v-show="index === currentSlide"
        class="absolute inset-0"
      >
        <img :src="slide.image" :alt="slide.title" class="w-full h-full object-cover" />
        <!-- Dark overlay -->
        <div class="absolute inset-0 bg-gradient-to-r from-[#0B0F19]/95 via-[#0B0F19]/60 to-[#0B0F19]/30" />
        <div class="absolute inset-0 bg-gradient-to-t from-[#0B0F19] via-transparent to-[#0B0F19]/40" />
      </div>
    </TransitionGroup>

    <!-- ============ Top nav bar ============ -->
    <header class="absolute top-0 left-0 right-0 z-30 flex items-center justify-between px-10 py-5">
      <!-- Logo -->
      <div class="flex items-center gap-3">
        <img src="/images/logo/favicon.svg" alt="农作物疾病检测系统" class="w-9 h-9 rounded-lg shadow-lg shadow-[#FF6A00]/20" />
        <span class="text-lg font-bold text-white tracking-wide">农作物疾病检测系统</span>
        <span class="text-[10px] text-slate-500 font-mono uppercase tracking-widest ml-1">AGRI-DETECTION</span>
      </div>
    </header>

    <!-- ============ Center content ============ -->
    <div class="absolute inset-0 z-20 flex items-center justify-center lg:justify-start">
      <!-- Left side: big title + slide info -->
      <div class="flex-1 pl-6 lg:pl-10 pr-4 lg:pr-6 max-w-2xl hidden lg:block">
        <!-- Tag -->
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#FF6A00]/10 border border-[#FF6A00]/20 mb-6">
          <span class="w-1.5 h-1.5 rounded-full bg-[#FF6A00] animate-pulse" />
          <span class="text-[11px] text-[#FF6A00] font-mono uppercase tracking-wider">WELCOME TO AGRI-DETECTION</span>
        </div>

        <!-- Big title -->
        <h1 class="text-4xl lg:text-5xl font-black text-white leading-tight mb-4">
          <span class="text-transparent bg-clip-text bg-gradient-to-r from-white via-white to-white/60">智慧农业</span><br />
          <span class="text-transparent bg-clip-text bg-gradient-to-r from-[#FF6A00] to-[#FFB300]">遥测监控平台</span>
        </h1>

        <p class="text-sm text-slate-400 leading-relaxed mb-8 max-w-md">
          基于 2.5D 可视化与 AI 推理的农作物健康监测系统，实时感知每一寸土地的生命脉搏。
        </p>

        <!-- Slide info card -->
        <div class="flex items-center gap-4 mb-8">
          <div class="flex items-center gap-3 px-4 py-2.5 rounded-xl bg-white/5 backdrop-blur-md border border-white/10">
            <img :src="slides[currentSlide].image" class="w-10 h-10 rounded-lg object-cover" />
            <div>
              <div class="text-sm text-white font-medium">{{ slides[currentSlide].title }}</div>
              <div class="text-[11px] text-slate-500">{{ slides[currentSlide].desc }}</div>
            </div>
          </div>
          <a
            :href="slides[currentSlide].link"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-[#FF6A00] to-[#FFB300] text-[#0B0F19] text-sm font-bold hover:shadow-lg hover:shadow-[#FF6A00]/25 transition-all hover:scale-105"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
              <path d="M13.5 6H5.25A2.25 2.25 0 003 8.25v10.5A2.25 2.25 0 005.25 21h10.5A2.25 2.25 0 0018 18.75V10.5m-10.5 6L21 3m0 0h-5.25M21 3v5.25" />
            </svg>
            访问资源
          </a>
        </div>

        <!-- Carousel controls -->
        <div class="flex items-center gap-4">
          <!-- Prev / Next -->
          <div class="flex gap-2">
            <button
              class="w-8 h-8 rounded-lg border border-white/10 bg-white/5 backdrop-blur flex items-center justify-center text-slate-400 hover:text-white hover:border-white/20 hover:bg-white/10 transition-all"
              @click="prevSlide(); restartTimer()"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><path d="M15.75 19.5L8.25 12l7.5-7.5" /></svg>
            </button>
            <button
              class="w-8 h-8 rounded-lg border border-white/10 bg-white/5 backdrop-blur flex items-center justify-center text-slate-400 hover:text-white hover:border-white/20 hover:bg-white/10 transition-all"
              @click="nextSlide(); restartTimer()"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><path d="M8.25 4.5l7.5 7.5-7.5 7.5" /></svg>
            </button>
          </div>

          <!-- Slide number -->
          <div class="flex items-center gap-1.5">
            <span class="text-lg font-black font-mono text-white">{{ String(currentSlide + 1).padStart(2, '0') }}</span>
            <span class="text-xs text-slate-600 font-mono">/ {{ String(slides.length).padStart(2, '0') }}</span>
          </div>

          <!-- Dots with progress -->
          <div class="flex gap-2 items-center">
            <button
              v-for="(_, index) in slides"
              :key="index"
              class="group relative h-1 rounded-full overflow-hidden transition-all duration-300"
              :class="index === currentSlide ? 'w-12' : 'w-4 hover:w-6'"
              @click="goTo(index)"
            >
              <span class="absolute inset-0 bg-white/10 rounded-full" />
              <span
                v-show="index === currentSlide"
                class="absolute inset-y-0 left-0 bg-gradient-to-r from-[#FF6A00] to-[#FFB300] rounded-full transition-none"
                :style="{ width: progress + '%' }"
              />
              <span
                v-show="index !== currentSlide"
                class="absolute inset-0 bg-white/30 rounded-full scale-x-0 group-hover:scale-x-100 origin-left transition-transform"
              />
            </button>
          </div>

          <!-- Pause indicator -->
          <button
            class="w-7 h-7 rounded-md flex items-center justify-center text-slate-500 hover:text-white hover:bg-white/5 transition-all"
            @click="isHovering ? (isHovering = false, restartTimer()) : (isHovering = true, stopTimer())"
          >
            <svg v-if="!isHovering" class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24"><path d="M6 4h4v16H6zM14 4h4v16h-4z" /></svg>
            <svg v-else class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z" /></svg>
          </button>
        </div>
      </div>

      <!-- ============ Right: Login card ============ -->
      <div class="mr-6 lg:mr-10 w-full max-w-[520px] shrink-0">
        <div class="rounded-2xl bg-[#111827]/70 backdrop-blur-2xl border border-white/[0.06] shadow-2xl overflow-hidden">
          <!-- Card header -->
          <div class="px-8 pt-6 pb-0">
            <div class="flex items-center gap-2 mb-1">
              <div class="w-2 h-2 rounded-full bg-[#4ADE80] animate-pulse" />
              <span class="text-[10px] text-[#4ADE80] font-mono uppercase tracking-widest">WAITING FOR APPROVAL</span>
            </div>
            <h2 class="text-lg font-bold text-white mb-1">加入企业</h2>
            <p class="text-xs text-slate-500 mb-4">请输入您的企业邀请码以继续</p>
          </div>

          <!-- Card body -->
          <div class="px-8 pb-6">
            <!-- Welcome -->
            <div class="rounded-xl bg-white/[0.03] border border-white/[0.04] p-3 mb-4">
              <p class="text-xs text-slate-400">
                欢迎，<span class="text-white font-medium">{{ auth.userInfo?.username }}</span>
              </p>
            </div>

            <!-- Input -->
            <div class="mb-3">
              <label class="block text-[10px] text-slate-500 mb-2 uppercase tracking-wider font-mono">INVITE CODE</label>
              <div class="relative">
                <input
                  v-model="inviteCode"
                  type="text"
                  placeholder="输入邀请码..."
                  class="w-full px-4 py-3 rounded-xl bg-white/[0.04] border border-white/[0.08] text-white placeholder-slate-600 text-sm font-mono tracking-[0.2em] focus:outline-none focus:border-[#FF6A00]/40 focus:ring-1 focus:ring-[#FF6A00]/10 transition-all"
                  @keyup.enter="handleJoin"
                />
                <div class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-600">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" /></svg>
                </div>
              </div>
            </div>

            <!-- Result -->
            <div
              v-if="result"
              class="mb-3 px-3 py-2 rounded-lg text-xs flex items-center gap-2"
              :class="result.success
                ? 'bg-[#4ADE80]/10 border border-[#4ADE80]/20 text-[#4ADE80]'
                : 'bg-[#EF4444]/10 border border-[#EF4444]/20 text-[#EF4444]'"
            >
              <svg v-if="result.success" class="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              <svg v-else class="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
              {{ result.message }}
            </div>

            <!-- Actions -->
            <button
              class="w-full py-3 rounded-xl text-sm font-bold transition-all disabled:opacity-40 disabled:cursor-not-allowed mb-3"
              :class="isValidating || !inviteCode.trim()
                ? 'bg-white/5 text-slate-500 border border-white/5'
                : 'bg-gradient-to-r from-[#FF6A00] to-[#FFB300] text-[#0B0F19] hover:shadow-lg hover:shadow-[#FF6A00]/20 hover:scale-[1.01]'"
              :disabled="isValidating || !inviteCode.trim()"
              @click="handleJoin"
            >
              {{ isValidating ? '验证中...' : '加入企业' }}
            </button>

            <button
              class="w-full py-2.5 rounded-xl bg-white/[0.03] border border-white/[0.06] text-slate-500 text-xs hover:text-slate-300 hover:bg-white/[0.06] transition-all"
              @click="handleLogout"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ Bottom: thumbnail strip ============ -->
    <div class="absolute bottom-6 left-10 right-10 z-30 flex items-end justify-between">
      <!-- Thumbnails -->
      <div class="flex gap-3">
        <button
          v-for="(slide, index) in slides"
          :key="slide.link"
          class="relative rounded-xl overflow-hidden border-2 transition-all duration-300 group"
          :class="index === currentSlide
            ? 'border-[#FF6A00] shadow-lg shadow-[#FF6A00]/20 scale-105'
            : 'border-white/10 hover:border-white/20 opacity-50 hover:opacity-80'"
          @click="goTo(index)"
        >
          <img :src="slide.image" class="w-32 h-16 object-cover" />
          <div class="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
          <span class="absolute bottom-1 left-2 text-[9px] text-white/80 font-medium">{{ slide.title }}</span>
        </button>
      </div>

      <!-- Bottom right hint -->
      <div class="flex items-center gap-2 text-slate-600">
        <span class="text-[10px] font-mono">SCROLL TO EXPLORE</span>
        <svg class="w-3 h-3 animate-bounce" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M19.5 13.5L12 21m0 0l-7.5-7.5M12 21V3" /></svg>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bg-fade-enter-active,
.bg-fade-leave-active {
  transition: opacity 0.8s ease;
}
.bg-fade-enter-from,
.bg-fade-leave-to {
  opacity: 0;
}
</style>

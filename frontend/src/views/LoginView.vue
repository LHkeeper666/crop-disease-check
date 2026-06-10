<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import GlassCard from '../components/GlassCard.vue'
import GlowButton from '../components/GlowButton.vue'

const router = useRouter()
const auth = useAuthStore()

// Meteor effect
const canvasRef = ref<HTMLCanvasElement>()
let animationId: number | null = null

interface Meteor {
  x: number
  y: number
  length: number
  speed: number
  opacity: number
  angle: number
}

function initMeteorEffect() {
  const canvasEl = canvasRef.value
  if (!canvasEl) return

  const ctxEl = canvasEl.getContext('2d')
  if (!ctxEl) return

  const canvas = canvasEl
  const ctx = ctxEl

  const resize = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  const meteors: Meteor[] = []
  const maxMeteors = 20

  function createMeteor(): Meteor {
    return {
      x: Math.random() * canvas.width * 1.5 - canvas.width * 0.25,
      y: -50,
      length: Math.random() * 100 + 50,
      speed: Math.random() * 5 + 4,
      opacity: Math.random() * 0.7 + 0.3,
      angle: Math.PI / 4 + (Math.random() - 0.5) * 0.3,
    }
  }

  function animate() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    // Add new meteors randomly
    if (meteors.length < maxMeteors && Math.random() < 0.08) {
      meteors.push(createMeteor())
    }

    // Update and draw meteors
    for (let i = meteors.length - 1; i >= 0; i--) {
      const m = meteors[i]
      m.x += Math.cos(m.angle) * m.speed
      m.y += Math.sin(m.angle) * m.speed
      m.opacity -= 0.003

      if (m.opacity <= 0 || m.y > canvas.height + 50) {
        meteors.splice(i, 1)
        continue
      }

      // Draw meteor trail
      const gradient = ctx.createLinearGradient(
        m.x, m.y,
        m.x - Math.cos(m.angle) * m.length,
        m.y - Math.sin(m.angle) * m.length
      )
      gradient.addColorStop(0, `rgba(255, 255, 255, ${m.opacity})`)
      gradient.addColorStop(0.3, `rgba(74, 222, 128, ${m.opacity * 0.6})`)
      gradient.addColorStop(1, 'rgba(74, 222, 128, 0)')

      ctx.beginPath()
      ctx.moveTo(m.x, m.y)
      ctx.lineTo(
        m.x - Math.cos(m.angle) * m.length,
        m.y - Math.sin(m.angle) * m.length
      )
      ctx.strokeStyle = gradient
      ctx.lineWidth = 1.5
      ctx.stroke()

      // Draw meteor head glow
      ctx.beginPath()
      ctx.arc(m.x, m.y, 2, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(255, 255, 255, ${m.opacity})`
      ctx.fill()
    }

    animationId = requestAnimationFrame(animate)
  }

  animate()
}

onMounted(() => {
  initMeteorEffect()
})

onUnmounted(() => {
  if (animationId) cancelAnimationFrame(animationId)
})

const activeTab = ref<'login' | 'register'>('login')

// Login
const loginUsername = ref('')
const loginPassword = ref('')

// Register
const regEmail = ref('')
const regUsername = ref('')
const regPassword = ref('')
const regConfirmPassword = ref('')
const regOtp = ref('')
const otpSent = ref(false)
const otpCooldown = ref(0)

const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

function clearMessages() {
  errorMsg.value = ''
  successMsg.value = ''
}

function startCooldown() {
  otpCooldown.value = 60
  const timer = setInterval(() => {
    otpCooldown.value--
    if (otpCooldown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function handleSendOtp() {
  if (!regEmail.value) {
    errorMsg.value = '请输入邮箱地址'
    return
  }
  clearMessages()
  loading.value = true
  const ok = await auth.sendOtp(regEmail.value)
  loading.value = false
  if (ok) {
    otpSent.value = true
    startCooldown()
    successMsg.value = '验证码已发送（演示模式，见下方提示）'
  }
}

async function handleLogin() {
  if (!loginUsername.value || !loginPassword.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  clearMessages()
  loading.value = true
  const result = await auth.login(loginUsername.value, loginPassword.value)
  loading.value = false
  if (result.success) {
    if (result.pending) {
      localStorage.setItem('treeforge_user_pending', 'true')
      router.push('/pending')
    } else {
      localStorage.removeItem('treeforge_user_pending')
      router.push('/dashboard')
    }
  } else {
    errorMsg.value = '用户名或密码错误'
  }
}

async function handleRegister() {
  if (!regEmail.value || !regUsername.value || !regPassword.value || !regOtp.value) {
    errorMsg.value = '请填写完整注册信息'
    return
  }
  if (regPassword.value !== regConfirmPassword.value) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  if (regPassword.value.length < 6) {
    errorMsg.value = '密码长度不少于6位'
    return
  }
  clearMessages()
  loading.value = true
  const ok = await auth.register(regEmail.value, regUsername.value, regPassword.value)
  loading.value = false
  if (ok) {
    // Auto login after registration, then redirect to pending
    const loginResult = await auth.login(regUsername.value, regPassword.value)
    if (loginResult.success && loginResult.pending) {
      localStorage.setItem('treeforge_user_pending', 'true')
      router.push('/pending')
    } else {
      successMsg.value = '注册成功，请登录'
      activeTab.value = 'login'
      loginUsername.value = regUsername.value
    }
  } else {
    errorMsg.value = '该用户名已被注册'
  }
}
</script>

<template>
  <div class="min-h-screen relative overflow-hidden flex flex-col items-center justify-center">
    <!-- Background image layer -->
    <div class="absolute inset-0 z-0 overflow-hidden">
      <img
        src="/images/bg/login-bg.png"
        alt=""
        class="absolute inset-0 w-full h-full object-cover"
        style="filter: blur(2px) brightness(0.45); transform: scale(1.1);"
      />
    </div>
    <!-- Dark overlay -->
    <div class="absolute inset-0 z-[1] bg-base/40" />
    <!-- Meteor canvas -->
    <canvas ref="canvasRef" class="absolute inset-0 z-[2] pointer-events-none" />

    <!-- Floating decorative elements -->
    <div class="absolute top-20 left-[10%] w-72 h-72 bg-cyber-green/8 rounded-full blur-[80px] z-[2]" />
    <div class="absolute bottom-20 right-[10%] w-64 h-64 bg-sunset-from/6 rounded-full blur-[80px] z-[2]" />

    <!-- Leaf decoration - top right -->
    <svg class="absolute top-8 right-12 w-20 h-20 text-cyber-green/10 z-[2]" viewBox="0 0 100 100" fill="none">
      <path d="M50 10 C20 30, 10 60, 50 90 C90 60, 80 30, 50 10Z" stroke="currentColor" stroke-width="1.5" fill="currentColor" fill-opacity="0.05"/>
      <path d="M50 10 L50 90" stroke="currentColor" stroke-width="0.8"/>
      <path d="M50 30 C35 40, 25 50, 20 55" stroke="currentColor" stroke-width="0.6"/>
      <path d="M50 50 C65 55, 75 60, 80 65" stroke="currentColor" stroke-width="0.6"/>
    </svg>

    <!-- Leaf decoration - bottom left -->
    <svg class="absolute bottom-12 left-16 w-16 h-16 text-cyber-green/8 z-[2] rotate-[-30deg]" viewBox="0 0 100 100" fill="none">
      <path d="M50 10 C20 30, 10 60, 50 90 C90 60, 80 30, 50 10Z" stroke="currentColor" stroke-width="1.5" fill="currentColor" fill-opacity="0.05"/>
      <path d="M50 10 L50 90" stroke="currentColor" stroke-width="0.8"/>
    </svg>

    <!-- Wheat/grain decoration - left -->
    <svg class="absolute top-1/3 left-8 w-12 h-32 text-cyber-green/6 z-[2]" viewBox="0 0 50 120" fill="none">
      <path d="M25 120 L25 20" stroke="currentColor" stroke-width="1"/>
      <ellipse cx="25" cy="20" rx="8" ry="5" stroke="currentColor" stroke-width="0.8" fill="currentColor" fill-opacity="0.05"/>
      <ellipse cx="18" cy="35" rx="7" ry="4" stroke="currentColor" stroke-width="0.8" fill="currentColor" fill-opacity="0.05" transform="rotate(-20,18,35)"/>
      <ellipse cx="32" cy="35" rx="7" ry="4" stroke="currentColor" stroke-width="0.8" fill="currentColor" fill-opacity="0.05" transform="rotate(20,32,35)"/>
      <ellipse cx="16" cy="50" rx="6" ry="3.5" stroke="currentColor" stroke-width="0.8" fill="currentColor" fill-opacity="0.05" transform="rotate(-25,16,50)"/>
      <ellipse cx="34" cy="50" rx="6" ry="3.5" stroke="currentColor" stroke-width="0.8" fill="currentColor" fill-opacity="0.05" transform="rotate(25,34,50)"/>
    </svg>

    <!-- Content -->
    <div class="relative z-10 flex flex-col items-center w-full max-w-lg mx-4">
      <!-- Title area -->
      <div class="text-center mb-8">
        <!-- Leaf icon above title -->
        <div class="mx-auto mb-4 w-14 h-14 rounded-2xl bg-gradient-to-br from-cyber-green to-cyber-green-dark flex items-center justify-center shadow-lg shadow-cyber-green/20">
          <svg class="w-8 h-8 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c1.5 0 3-.3 4.3-.9"/>
            <path d="M12 2c3 3 5 7 5 10s-2 7-5 10"/>
            <path d="M7 8c1.5 1 3 2.5 5 3"/>
            <path d="M7 14c2-.5 3.5-1.5 5-3"/>
            <circle cx="19" cy="5" r="3" fill="currentColor" fill-opacity="0.3"/>
            <path d="M17.5 5h3M19 3.5v3" stroke-width="1.5"/>
          </svg>
        </div>
        <h1 class="text-3xl font-bold tracking-wide mb-2">
          <span class="text-sunset">农作物疾病检测系统</span>
        </h1>
        <p class="text-sm text-slate-400 font-mono tracking-widest uppercase">
          农作物疾病监测系统
        </p>
        <!-- Decorative line -->
        <div class="mx-auto mt-4 w-24 h-px bg-gradient-to-r from-transparent via-cyber-green/40 to-transparent" />
      </div>

      <!-- Login card -->
      <GlassCard class="w-full p-8">
        <!-- Tab switcher -->
        <div class="flex mb-6 p-1 rounded-xl bg-white/5">
          <button
            class="flex-1 py-2 rounded-lg text-sm font-medium transition-all duration-300"
            :class="activeTab === 'login' ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-slate-300'"
            @click="activeTab = 'login'; clearMessages()"
          >
            登 录
          </button>
          <button
            class="flex-1 py-2 rounded-lg text-sm font-medium transition-all duration-300"
            :class="activeTab === 'register' ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-slate-300'"
            @click="activeTab = 'register'; clearMessages()"
          >
            注 册
          </button>
        </div>

        <!-- Messages -->
        <div v-if="errorMsg" class="mb-4 px-4 py-2.5 rounded-lg bg-sakura/10 border border-sakura/20 text-sakura text-sm">
          {{ errorMsg }}
        </div>
        <div v-if="successMsg" class="mb-4 px-4 py-2.5 rounded-lg bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm">
          {{ successMsg }}
        </div>

        <!-- Demo OTP display -->
        <div v-if="auth.showMockOtp" class="mb-4 px-4 py-3 rounded-lg bg-sunset-from/10 border border-sunset-from/20">
          <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-1">DEMO MODE — 模拟验证码</div>
          <div class="flex items-center gap-3">
            <span class="text-2xl font-mono font-bold text-sunset tracking-[0.3em]">{{ auth.mockOtp }}</span>
            <button
              type="button"
              class="text-xs text-slate-400 hover:text-white transition-colors"
              @click="regOtp = auth.mockOtp"
            >
              填入
            </button>
          </div>
          <div class="text-[10px] text-slate-600 mt-1 font-mono">30s 后自动隐藏 · 后端完成后将替换为真实邮件发送</div>
        </div>

        <!-- Login form -->
        <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" class="space-y-4">
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">用户名</label>
            <input
              v-model="loginUsername"
              type="text"
              placeholder="请输入用户名"
              class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
            />
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">密码</label>
            <input
              v-model="loginPassword"
              type="password"
              placeholder="请输入密码"
              class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
            />
          </div>
          <GlowButton
            label="登 录"
            type="submit"
            :loading="loading"
            :disabled="!loginUsername || !loginPassword || loading"
            class="w-full"
            @click="handleLogin"
          />
        </form>

        <!-- Register form -->
        <form v-else @submit.prevent="handleRegister" class="space-y-4">
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">邮箱地址</label>
            <div class="flex gap-3">
              <input
                v-model="regEmail"
                type="email"
                placeholder="请输入QQ邮箱"
                class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
              />
              <button
                type="button"
                :disabled="otpCooldown > 0 || loading"
                class="px-4 py-3 rounded-xl text-sm font-medium whitespace-nowrap transition-all"
                :class="otpCooldown > 0 ? 'bg-white/5 text-slate-600 cursor-not-allowed' : 'bg-cyber-green/10 text-cyber-green border border-cyber-green/20 hover:bg-cyber-green/20'"
                @click="handleSendOtp"
              >
                {{ otpCooldown > 0 ? `${otpCooldown}s` : '发送验证码' }}
              </button>
            </div>
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">验证码</label>
            <input
              v-model="regOtp"
              type="text"
              placeholder="请输入6位验证码"
              maxlength="6"
              class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
            />
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">用户名</label>
            <input
              v-model="regUsername"
              type="text"
              placeholder="请设置用户名"
              class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
            />
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">密码</label>
            <input
              v-model="regPassword"
              type="password"
              placeholder="6-12位字母+数字"
              class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
            />
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 uppercase tracking-wider">确认密码</label>
            <input
              v-model="regConfirmPassword"
              type="password"
              placeholder="请再次输入密码"
              class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
            />
          </div>
          <GlowButton
            label="注 册"
            type="submit"
            :loading="loading"
            :disabled="!regEmail || !regUsername || !regPassword || !regOtp || loading"
            class="w-full"
            @click="handleRegister"
          />
        </form>

        <!-- Footer -->
        <div class="mt-6 text-center text-xs text-slate-600">
          <span class="font-mono">v1.0.0</span>
          <span class="mx-2">|</span>
          <span>智慧农业病虫害无人值守监测系统</span>
        </div>
      </GlassCard>

      <!-- Bottom decorative text -->
      <div class="mt-6 text-center text-[10px] text-slate-600/50 font-mono tracking-wider">
        POWERED BY TREEFORGE AGRI-TELEMETRY ENGINE
      </div>
    </div>
  </div>
</template>

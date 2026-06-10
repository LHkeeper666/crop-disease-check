<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import GlassCard from '../components/GlassCard.vue'

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

  // Simulate network delay
  await new Promise(r => setTimeout(r, 600))

  const validation = auth.validateInviteCode(inviteCode.value.trim())
  if (!validation.success) {
    result.value = validation
    isValidating.value = false
    return
  }

  const joined = auth.joinCompany(inviteCode.value.trim())
  isValidating.value = false

  if (joined) {
    result.value = { success: true, message: `已加入 ${validation.companyName}，正在跳转...`, companyName: validation.companyName }
    setTimeout(() => {
      router.push('/dashboard')
    }, 1500)
  } else {
    result.value = { success: false, message: '加入失败，请重试' }
  }
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-base relative overflow-hidden">
    <!-- Background decorations -->
    <div class="absolute top-20 left-[15%] w-80 h-80 bg-cyber-green/5 rounded-full blur-[100px]" />
    <div class="absolute bottom-20 right-[15%] w-64 h-64 bg-sunset-from/5 rounded-full blur-[80px]" />

    <GlassCard class="w-full max-w-md mx-4 p-8 text-center">
      <!-- Icon -->
      <div class="w-20 h-20 rounded-full bg-cyber-green/10 border border-cyber-green/20 flex items-center justify-center mx-auto mb-6">
        <svg class="w-10 h-10 text-cyber-green" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
          <path d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
        </svg>
      </div>

      <!-- Title -->
      <h1 class="text-xl font-bold text-white mb-2">加入企业</h1>
      <p class="text-xs text-slate-500 font-mono mb-6">JOIN YOUR ORGANIZATION</p>

      <!-- Message -->
      <div class="glass rounded-xl p-4 mb-6 text-left">
        <p class="text-sm text-slate-300 leading-relaxed">
          欢迎 <span class="text-white font-medium">{{ auth.userInfo?.username }}</span>！
          请输入企业邀请码以加入您的团队，即可访问系统全部功能。
        </p>
      </div>

      <!-- Invite code input -->
      <div class="mb-4">
        <label class="block text-xs text-slate-400 mb-2 uppercase tracking-wider text-left">企业邀请码</label>
        <input
          v-model="inviteCode"
          type="text"
          placeholder="请输入邀请码，如 TF2026"
          class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm font-mono tracking-wider focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
          @keyup.enter="handleJoin"
        />
      </div>

      <!-- Result message -->
      <div v-if="result" class="mb-4 px-4 py-2.5 rounded-lg text-sm" :class="result.success ? 'bg-cyber-green/10 border border-cyber-green/20 text-cyber-green' : 'bg-sakura/10 border border-sakura/20 text-sakura'">
        {{ result.message }}
      </div>

      <!-- Actions -->
      <div class="flex gap-3">
        <button
          class="flex-1 px-4 py-3 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isValidating || !inviteCode.trim()"
          @click="handleJoin"
        >
          {{ isValidating ? '验证中...' : '加入企业' }}
        </button>
        <button
          class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-slate-400 text-sm hover:bg-white/10 transition-colors"
          @click="handleLogout"
        >
          退出登录
        </button>
      </div>

      <!-- Demo hint -->
      <div class="mt-6 glass rounded-xl p-3 text-left">
        <div class="text-[10px] text-slate-500 uppercase tracking-wider mb-2">演示邀请码</div>
        <div class="space-y-1">
          <div class="flex justify-between text-xs">
            <span class="text-slate-400">TreeForge 智慧农场</span>
            <span class="text-cyber-green font-mono">TF2026</span>
          </div>
          <div class="flex justify-between text-xs">
            <span class="text-slate-400">绿丰农业科技</span>
            <span class="text-cyber-green font-mono">AG2026</span>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

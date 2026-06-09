<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const navItems = [
  { path: '/dashboard', label: '遥测总览', icon: '&#9673;' },
  { path: '/workorders', label: '智能工单', icon: '&#9881;' },
  { path: '/devices', label: '设备管理', icon: '&#9881;' },
  { path: '/reports', label: '农情报表', icon: '&#9776;' },
]

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <aside class="w-64 h-full flex flex-col glass border-r border-white/5">
    <!-- Logo -->
    <div class="px-6 py-5 border-b border-white/5">
      <div class="flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-gradient-to-br from-cyber-green to-cyber-green-dark flex items-center justify-center text-base font-bold text-white">
          TF
        </div>
        <div>
          <div class="text-sm font-semibold text-white tracking-wide">TreeForge</div>
          <div class="text-[10px] text-slate-500 font-mono">AGRI-TELEMETRY v1.0</div>
        </div>
      </div>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
      <button
        v-for="item in navItems"
        :key="item.path"
        class="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-all duration-200"
        :class="route.path === item.path
          ? 'bg-white/10 text-white border border-white/10'
          : 'text-slate-400 hover:text-white hover:bg-white/5'"
        @click="router.push(item.path)"
      >
        <span class="text-base" v-html="item.icon" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <!-- User card -->
    <div class="px-4 py-4 border-t border-white/5">
      <div class="flex items-center gap-3 mb-3">
        <div class="w-8 h-8 rounded-full bg-gradient-to-br from-sunset-from to-sunset-to flex items-center justify-center text-xs font-bold text-white">
          {{ auth.userName.charAt(0) }}
        </div>
        <div class="flex-1 min-w-0">
          <div class="text-sm text-white truncate">{{ auth.userName }}</div>
          <div class="text-[10px] text-slate-500 font-mono">{{ auth.userRole }}</div>
        </div>
      </div>
      <button
        class="w-full text-xs text-slate-500 hover:text-sakura transition-colors py-1.5 rounded-lg hover:bg-sakura/5"
        @click="handleLogout"
      >
        退出登录
      </button>
    </div>
  </aside>
</template>

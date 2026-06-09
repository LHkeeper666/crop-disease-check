<script setup lang="ts">
import { ref } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import GlowButton from '../components/GlowButton.vue'
import { mockCameras, mockUsers, mockGridHeatmap } from '../mock/data'

const activeTab = ref<'cameras' | 'grids' | 'users'>('cameras')

const cameras = ref(mockCameras)
const grids = ref(mockGridHeatmap)
const users = ref(mockUsers)

const statusColors: Record<string, string> = {
  ONLINE: 'text-cyber-green bg-cyber-green/10 border-cyber-green/20',
  OFFLINE: 'text-slate-400 bg-white/5 border-white/10',
  FAULT: 'text-sakura bg-sakura/10 border-sakura/20',
  ACTIVE: 'text-cyber-green bg-cyber-green/10 border-cyber-green/20',
  DISABLED: 'text-slate-400 bg-white/5 border-white/10',
}

const roleLabels: Record<string, string> = {
  ADMIN: '管理员',
  EXPERT: '农技专家',
  MANAGER: '农场管理者',
  VISITOR: '访客',
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
        @click="activeTab = tab.key as any"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Content -->
    <GlassCard class="flex-1 min-h-0 overflow-hidden flex flex-col">
      <!-- Cameras -->
      <div v-if="activeTab === 'cameras'" class="flex-1 overflow-y-auto">
        <div class="flex justify-between items-center mb-4">
          <span class="text-xs text-slate-400 font-mono">{{ cameras.length }} 台设备</span>
          <GlowButton label="+ 添加摄像头" />
        </div>
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs text-slate-500 uppercase tracking-wider border-b border-white/5">
              <th class="pb-3 pr-4">名称</th>
              <th class="pb-3 pr-4">状态</th>
              <th class="pb-3 pr-4">覆盖网格</th>
              <th class="pb-3 pr-4">RTSP</th>
              <th class="pb-3">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cam in cameras" :key="cam.id" class="border-b border-white/5 hover:bg-white/5 transition-colors">
              <td class="py-3 pr-4 text-white font-medium">{{ cam.name }}</td>
              <td class="py-3 pr-4">
                <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="statusColors[cam.status]">
                  {{ cam.status }}
                </span>
              </td>
              <td class="py-3 pr-4 text-slate-400 font-mono text-xs">{{ cam.grid }}</td>
              <td class="py-3 pr-4 text-slate-500 font-mono text-xs truncate max-w-40">{{ cam.rtspUrl }}</td>
              <td class="py-3">
                <div class="flex gap-2">
                  <button class="text-xs text-cyber-green hover:underline">编辑</button>
                  <button class="text-xs text-sakura hover:underline">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Grids -->
      <div v-if="activeTab === 'grids'" class="flex-1 overflow-y-auto">
        <div class="flex justify-between items-center mb-4">
          <span class="text-xs text-slate-400 font-mono">{{ grids.length }} 个网格</span>
          <GlowButton label="+ 添加网格" />
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
                :class="grid.status === 'critical' ? 'bg-sakura pulse-red' : grid.status === 'warning' ? 'bg-amber' : 'bg-cyber-green pulse-green'"
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
      <div v-if="activeTab === 'users'" class="flex-1 overflow-y-auto">
        <div class="flex justify-between items-center mb-4">
          <span class="text-xs text-slate-400 font-mono">{{ users.length }} 位用户</span>
          <GlowButton label="+ 新增用户" />
        </div>
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs text-slate-500 uppercase tracking-wider border-b border-white/5">
              <th class="pb-3 pr-4">用户名</th>
              <th class="pb-3 pr-4">姓名</th>
              <th class="pb-3 pr-4">角色</th>
              <th class="pb-3 pr-4">状态</th>
              <th class="pb-3 pr-4">最后登录</th>
              <th class="pb-3">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id" class="border-b border-white/5 hover:bg-white/5 transition-colors">
              <td class="py-3 pr-4 text-white font-mono text-xs">{{ user.username }}</td>
              <td class="py-3 pr-4 text-white">{{ user.name }}</td>
              <td class="py-3 pr-4">
                <span class="text-xs text-slate-400">{{ roleLabels[user.role] || user.role }}</span>
              </td>
              <td class="py-3 pr-4">
                <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="statusColors[user.status]">
                  {{ user.status }}
                </span>
              </td>
              <td class="py-3 pr-4 text-xs text-slate-500 font-mono">{{ user.lastLoginAt?.replace('T', ' ').slice(0, 16) }}</td>
              <td class="py-3">
                <div class="flex gap-2">
                  <button class="text-xs text-cyber-green hover:underline">编辑</button>
                  <button class="text-xs text-amber hover:underline">重置密码</button>
                  <button class="text-xs text-sakura hover:underline">禁用</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </GlassCard>
  </div>
</template>

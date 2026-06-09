<script setup lang="ts">
import { ref, computed } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import GlowButton from '../components/GlowButton.vue'
import { mockWorkOrders } from '../mock/data'

const orders = ref(mockWorkOrders)
const filterStatus = ref<string>('ALL')
const filterSeverity = ref<string>('ALL')

const severityConfig: Record<string, { label: string; color: string; glow: string }> = {
  CRITICAL: { label: '紧急', color: 'text-sakura bg-sakura/10 border-sakura/20', glow: 'glow-red' },
  HIGH: { label: '高危', color: 'text-orange-400 bg-orange-400/10 border-orange-400/20', glow: '' },
  MEDIUM: { label: '中等', color: 'text-amber bg-amber/10 border-amber/20', glow: '' },
  LOW: { label: '低', color: 'text-slate-400 bg-white/5 border-white/10', glow: '' },
}

const statusConfig: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'text-amber bg-amber/10 border-amber/20' },
  PROCESSING: { label: '处理中', color: 'text-blue-400 bg-blue-400/10 border-blue-400/20' },
  DONE: { label: '已完成', color: 'text-cyber-green bg-cyber-green/10 border-cyber-green/20' },
  IGNORED: { label: '已忽略', color: 'text-slate-500 bg-white/5 border-white/10' },
  ESCALATED: { label: '已升级', color: 'text-sakura bg-sakura/10 border-sakura/20' },
}

const filteredOrders = computed(() => {
  return orders.value.filter(o => {
    if (filterStatus.value !== 'ALL' && o.status !== filterStatus.value) return false
    if (filterSeverity.value !== 'ALL' && o.severity !== filterSeverity.value) return false
    return true
  })
})

const stats = computed(() => ({
  total: orders.value.length,
  pending: orders.value.filter(o => o.status === 'PENDING').length,
  processing: orders.value.filter(o => o.status === 'PROCESSING').length,
  done: orders.value.filter(o => o.status === 'DONE').length,
}))
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">自动事件响应与智能工单流转舱</h1>
        <p class="text-xs text-slate-500 font-mono">EVENT-DRIVEN WORKORDER MANAGEMENT</p>
      </div>
      <GlowButton label="+ 手动创建工单" />
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-4 gap-3 shrink-0">
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
      <select v-model="filterStatus" class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-white focus:outline-none">
        <option value="ALL">全部状态</option>
        <option value="PENDING">待处理</option>
        <option value="PROCESSING">处理中</option>
        <option value="DONE">已完成</option>
        <option value="IGNORED">已忽略</option>
      </select>
      <select v-model="filterSeverity" class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-white focus:outline-none">
        <option value="ALL">全部等级</option>
        <option value="CRITICAL">紧急</option>
        <option value="HIGH">高危</option>
        <option value="MEDIUM">中等</option>
        <option value="LOW">低</option>
      </select>
    </div>

    <!-- Work order list -->
    <GlassCard class="flex-1 min-h-0 overflow-y-auto">
      <div class="space-y-3">
        <div
          v-for="order in filteredOrders"
          :key="order.id"
          class="glass rounded-xl p-4 hover:border-white/20 transition-all cursor-pointer"
          :class="severityConfig[order.severity]?.glow"
        >
          <div class="flex items-start justify-between mb-3">
            <div class="flex-1">
              <div class="text-sm font-medium text-white mb-1">{{ order.title }}</div>
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
              <span class="px-2 py-0.5 rounded-md text-[10px] font-mono border" :class="statusConfig[order.status]?.color">
                {{ statusConfig[order.status]?.label }}
              </span>
            </div>
          </div>

          <!-- Status flow visualization -->
          <div class="flex items-center gap-2 mt-3">
            <div
              v-for="(step, idx) in ['待处理', '处理中', '已完成']"
              :key="step"
              class="flex items-center gap-2"
            >
              <div
                class="w-2 h-2 rounded-full"
                :class="idx === 0 ? 'bg-amber' : idx === 1 ? 'bg-blue-400' : 'bg-cyber-green'"
                :style="{ opacity: (order.status === 'DONE' || (order.status === 'PROCESSING' && idx <= 1) || (order.status === 'PENDING' && idx === 0)) ? 1 : 0.2 }"
              />
              <span class="text-[10px] text-slate-500">{{ step }}</span>
              <div v-if="idx < 2" class="w-8 h-px bg-white/10" />
            </div>
          </div>

          <div class="flex justify-between items-center mt-3 pt-3 border-t border-white/5">
            <span class="text-[10px] text-slate-600 font-mono">{{ order.createdAt.replace('T', ' ').slice(0, 16) }}</span>
            <div class="flex gap-2">
              <button v-if="order.status === 'PENDING'" class="text-xs text-cyber-green hover:underline">确认处理</button>
              <button class="text-xs text-slate-400 hover:underline">查看详情</button>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

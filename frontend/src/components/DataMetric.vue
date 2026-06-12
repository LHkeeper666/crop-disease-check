<script setup lang="ts">
import { toRef, computed } from 'vue'
import { useCountUp } from '../composables/useCountUp'

const props = defineProps<{
  label: string
  value: string | number
  unit?: string
  status?: 'normal' | 'warning' | 'critical'
  icon?: string
}>()

const statusColors = {
  normal: 'bg-cyber-green',
  warning: 'bg-amber',
  critical: 'bg-sakura',
}

const numericValue = computed(() => {
  const n = typeof props.value === 'string' ? parseFloat(props.value) : props.value
  return isNaN(n) ? 0 : n
})

const decimals = computed(() => {
  const s = String(props.value)
  const dot = s.indexOf('.')
  return dot >= 0 ? s.length - dot - 1 : 0
})

const animatedValue = useCountUp(numericValue, 600, decimals.value)

const displayValue = computed(() => {
  if (typeof props.value === 'string' && isNaN(parseFloat(props.value))) return props.value
  return animatedValue.value
})
</script>

<template>
  <div class="glass rounded-xl p-4 flex flex-col gap-2">
    <div class="flex items-center justify-between">
      <span class="text-xs text-slate-400 uppercase tracking-wider">{{ label }}</span>
      <span
        v-if="status"
        class="w-2 h-2 rounded-full"
        :class="[statusColors[status], status === 'normal' ? 'pulse-green' : status === 'critical' ? 'pulse-red' : '']"
      />
    </div>
    <div class="flex items-baseline gap-1">
      <span class="text-2xl font-mono font-bold text-white">{{ displayValue }}</span>
      <span v-if="unit" class="text-sm font-mono text-slate-500">{{ unit }}</span>
    </div>
  </div>
</template>

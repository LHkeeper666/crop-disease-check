<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useCountUp } from '../composables/useCountUp'

const props = defineProps<{
  label: string
  value: string | number
  unit?: string
  status?: 'normal' | 'warning' | 'critical'
  icon?: string
  editable?: boolean
}>()

const emit = defineEmits<{
  'update:value': [value: number]
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

// Double-click editing
const isEditing = ref(false)
const editInput = ref<HTMLInputElement>()

function startEdit() {
  if (!props.editable) return
  isEditing.value = true
  nextTick(() => editInput.value?.focus())
}

function finishEdit(e: Event) {
  const val = parseFloat((e.target as HTMLInputElement).value)
  if (!isNaN(val)) {
    emit('update:value', val)
  }
  isEditing.value = false
}
</script>

<template>
  <div class="glass rounded-xl p-4 flex flex-col gap-2" :class="editable ? 'cursor-pointer hover:border-white/20 transition-colors' : ''" @dblclick="startEdit">
    <div class="flex items-center justify-between">
      <span class="text-xs text-slate-400 uppercase tracking-wider">{{ label }}</span>
      <span
        v-if="status"
        class="w-2 h-2 rounded-full"
        :class="[statusColors[status], status === 'normal' ? 'pulse-green' : status === 'critical' ? 'pulse-red' : '']"
      />
    </div>
    <div class="flex items-baseline gap-1">
      <template v-if="!isEditing">
        <span class="text-2xl font-mono font-bold text-white">{{ displayValue }}</span>
      </template>
      <template v-else>
        <input
          ref="editInput"
          type="number"
          :value="value"
          step="any"
          class="w-24 text-2xl font-mono font-bold text-white bg-transparent border-b border-cyber-green/50 outline-none"
          @blur="finishEdit"
          @keyup.enter="($event.target as HTMLInputElement).blur()"
        />
      </template>
      <span v-if="unit" class="text-sm font-mono text-slate-500">{{ unit }}</span>
    </div>
  </div>
</template>

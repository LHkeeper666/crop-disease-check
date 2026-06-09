<script setup lang="ts">
defineProps<{
  label: string
  variant?: 'primary' | 'secondary' | 'danger'
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit'
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    :type="type || 'button'"
    :disabled="disabled || loading"
    class="relative font-semibold rounded-full px-7 py-2.5 transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed"
    :class="[
      variant === 'secondary'
        ? 'bg-white/5 border border-white/10 text-white hover:bg-white/10 hover:border-white/20'
        : variant === 'danger'
        ? 'bg-sakura/20 border border-sakura/30 text-sakura hover:bg-sakura/30'
        : 'glow-btn'
    ]"
    @click="$emit('click')"
  >
    <span v-if="loading" class="inline-block animate-spin mr-2">&#9696;</span>
    <slot>{{ label }}</slot>
  </button>
</template>

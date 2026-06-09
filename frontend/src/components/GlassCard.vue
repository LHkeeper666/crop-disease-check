<script setup lang="ts">
defineProps<{
  className?: string
  hoverable?: boolean
}>()
</script>

<template>
  <div
    class="glass rounded-2xl p-5 transition-all duration-300"
    :class="[
      hoverable ? 'hover:border-white/20 hover:shadow-[0_0_30px_rgba(74,222,128,0.08)] cursor-pointer' : '',
      className || ''
    ]"
  >
    <slot />
  </div>
</template>

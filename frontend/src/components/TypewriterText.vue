<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps<{
  text: string
  speed?: number
  onComplete?: () => void
}>()

const displayed = ref('')
const isTyping = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
let idx = 0

function startTyping() {
  stop()
  displayed.value = ''
  idx = 0
  isTyping.value = true

  timer = setInterval(() => {
    if (idx < props.text.length) {
      displayed.value += props.text[idx]
      idx++
    } else {
      stop()
      props.onComplete?.()
    }
  }, props.speed ?? 18)
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  isTyping.value = false
}

// Skip to end on click
function skipToEnd() {
  stop()
  displayed.value = props.text
  props.onComplete?.()
}

watch(() => props.text, startTyping, { immediate: true })

onUnmounted(stop)
</script>

<template>
  <span @click="skipToEnd" class="cursor-pointer">
    {{ displayed }}<span v-if="isTyping" class="inline-block w-[2px] h-[1em] bg-cyber-green ml-0.5 align-text-bottom animate-pulse" />
  </span>
</template>

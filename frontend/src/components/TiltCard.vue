<script setup lang="ts">
import { ref, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  maxTilt?: number
}>(), {
  maxTilt: 4,
})

const cardRef = ref<HTMLDivElement>()
const tilt = ref({ x: 0, y: 0 })
const glare = ref({ x: 50, y: 50, opacity: 0 })
const isHovering = ref(false)
let raf: number | null = null

function onMouseEnter() {
  isHovering.value = true
}

function onMouseMove(e: MouseEvent) {
  if (!cardRef.value) return
  if (raf) cancelAnimationFrame(raf)

  raf = requestAnimationFrame(() => {
    const rect = cardRef.value!.getBoundingClientRect()
    const x = (e.clientX - rect.left) / rect.width
    const y = (e.clientY - rect.top) / rect.height

    tilt.value = {
      x: (y - 0.5) * -props.maxTilt * 4,
      y: (x - 0.5) * props.maxTilt * 4,
    }
    glare.value = { x: x * 100, y: y * 100, opacity: 0.15 }
  })
}

function onMouseLeave() {
  isHovering.value = false
  tilt.value = { x: 0, y: 0 }
  glare.value = { x: 50, y: 50, opacity: 0 }
}

onUnmounted(() => {
  if (raf) cancelAnimationFrame(raf)
})
</script>

<template>
  <div
    ref="cardRef"
    class="relative overflow-hidden"
    :style="{
      transform: isHovering ? `perspective(600px) rotateX(${tilt.x}deg) rotateY(${tilt.y}deg) scale(1.03)` : 'perspective(600px) rotateX(0deg) rotateY(0deg) scale(1)',
      transition: isHovering ? 'transform 0.1s ease-out' : 'transform 0.4s ease-out',
    }"
    @mouseenter="onMouseEnter"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
  >
    <div
      class="pointer-events-none absolute inset-0 rounded-2xl transition-opacity duration-300 z-10"
      :style="{
        background: `radial-gradient(circle at ${glare.x}% ${glare.y}%, rgba(255,255,255,0.15), transparent 60%)`,
        opacity: glare.opacity,
      }"
    />
    <slot />
  </div>
</template>

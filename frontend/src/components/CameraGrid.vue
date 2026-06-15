<script setup lang="ts">
import { computed } from 'vue'
import CameraMonitor from './CameraMonitor.vue'

interface CameraItem {
  id: string
  name: string
  streamUrl?: string   // IP Webcam HTTP 直连地址
  status?: string
}

const props = withDefaults(defineProps<{
  cameras: CameraItem[]
  columns?: number
}>(), {
  columns: 2,
})

const gridStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: `repeat(${props.columns}, 1fr)`,
  gap: '8px',
}))
</script>

<template>
  <div :style="gridStyle">
    <CameraMonitor
      v-for="cam in cameras"
      :key="cam.id"
      :camera-id="cam.id"
      :camera-name="cam.name"
      :stream-url="cam.streamUrl"
      :active="cam.status !== 'OFFLINE'"
    />
  </div>
</template>

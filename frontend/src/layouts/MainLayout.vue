<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppSidebar from '../components/AppSidebar.vue'
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-base">
    <AppSidebar />
    <main class="flex-1 overflow-y-auto bg-grid-dots">
      <RouterView />
    </main>
  </div>
</template>

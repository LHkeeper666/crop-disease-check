<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import { computed } from 'vue'
import DavePet from './components/DavePet.vue'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const auth = useAuthStore()

const showDavePet = computed(() => {
  // 公开页面（登录、注册、待审核）不显示
  if (route.meta.public) return false
  // 未登录不显示
  return auth.isLoggedIn
})
</script>

<template>
  <RouterView />
  <DavePet v-if="showDavePet" />
</template>

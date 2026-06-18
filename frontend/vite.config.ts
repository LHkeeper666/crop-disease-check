import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/api/v1/detect': {
        target: process.env.VITE_API_BASE || 'http://localhost:8000',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.VITE_API_BASE || 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
        rewrite: (path) => '/api' + path,
      },
      '/api/agri-brain/chat': {
        target: process.env.VITE_API_BASE || 'http://localhost:8080',
        changeOrigin: true,
        // SSE 需要禁用缓冲
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Accept', 'text/event-stream')
          })
        },
      },
      '/api': {
        target: process.env.VITE_API_BASE || 'http://localhost:8080',
        changeOrigin: true,
      },
      '/proxy/deepseek': {
        target: 'https://api.deepseek.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/deepseek/, ''),
      },
      '/proxy/xiaomi': {
        target: 'https://token-plan-cn.xiaomimimo.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/xiaomi/, ''),
      },
    },
  },
})

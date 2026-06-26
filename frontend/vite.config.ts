import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 普通 REST 走代理；SSE 流式接口已在 codegen.ts 中直连后端
        timeout: 0,
        proxyTimeout: 0,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/covers': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/preview': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/sites': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      },
  },
})

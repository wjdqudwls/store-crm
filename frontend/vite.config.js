import { fileURLToPath, URL } from 'node:url'
import process from 'node:process'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  // GitHub Pages 화면 데모는 /store-crm/ 아래에서 열리므로 빌드 때 VITE_BASE로 받는다. 없으면 '/'.
  base: process.env.VITE_BASE || '/',
  plugins: [
    vue(),
    vueDevTools(),
  ],
  // 개발 서버: /api 요청을 로컬 백엔드(8080)로 보낸다. 운영에서는 Spring Boot가 화면과 API를 같이 내보낸다 (ADR-013)
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})

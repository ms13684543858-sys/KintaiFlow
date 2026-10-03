import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 開発時は /api をバックエンド(8080)へ中継する（CORS 回避）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' }
  }
})

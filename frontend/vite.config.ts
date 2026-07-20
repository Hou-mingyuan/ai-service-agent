import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 19041,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:19040', changeOrigin: true },
      '/ws': { target: 'ws://127.0.0.1:19040', ws: true }
    }
  },
  preview: {
    host: '127.0.0.1',
    port: 19041,
    strictPort: true
  }
})

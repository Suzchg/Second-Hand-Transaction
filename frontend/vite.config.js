import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    allowedHosts: ['.cpolar.top', '.cpolar.cn', '.cpolar.cc'],
    proxy: {
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
})

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    // 排除 Playwright e2e 测试目录，避免 Vitest 载入这些文件
    exclude: ['**/src/__tests__/e2e/**'],
  },
})

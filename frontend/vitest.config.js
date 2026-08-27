import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    // 明确排除 Playwright e2e 目录
    exclude: ['**/src/__tests__/e2e/**', '**/e2e/**', '**/src/**/e2e/**'],
    // 只包含单元测试文件（按你项目的单元测试命名约定调整）
    include: ['src/__tests__/**/*.test.{js,ts}', 'src/**/*.test.{js,ts}'],
  },
})

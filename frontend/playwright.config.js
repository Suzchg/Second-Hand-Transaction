import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './src/__tests__/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 120000,
  expect: { timeout: 15000 },
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // 前端 dev server（/api、/uploads 已代理到 8088）
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 120000,
    },
    {
      // 后端：browser-e2e profile（独立库 secondhand_e2e，限流/演示数据关闭）
      command: 'node src/__tests__/e2e/start-backend.mjs',
      url: 'http://localhost:8088/api/categories',
      reuseExistingServer: true,
      timeout: 300000,
    },
  ],
})

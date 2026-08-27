import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, PASSWORD, adminLogin } from './helpers.js'
import { findOne } from './db.js'

test.describe('场景1：用户注册与登录', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('注册成功：跳转首页、获得身份并落库', async ({ page }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)

    // 已登录：出现「发布」入口
    await expect(page.getByRole('button', { name: '发布' })).toBeVisible()

    // 数据库落库：用户 + 登录标识
    const user = await findOne('SELECT id, role, status FROM users WHERE phone = ?', [phone])
    expect(user).not.toBeNull()
    expect(user.role).toBe('USER')
    expect(user.status).toBe('ACTIVE')

    const ident = await findOne('SELECT identifier, identity_type FROM user_identities WHERE identifier = ?', [phone])
    expect(ident).not.toBeNull()
    expect(ident.identity_type).toBe('PHONE')
  })

  test('重复注册：提示该手机号已注册', async ({ page }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)

    await page.goto('/login')
    await page.getByRole('button', { name: '注册', exact: true }).click()
    await page.getByPlaceholder('例如：13800138000').fill(phone)
    await page.getByPlaceholder('至少 6 位字符').fill(PASSWORD)
    await page.locator('button.submitBtn').click()

    await expect(page.getByText('该手机号已注册')).toBeVisible()
  })

  test('密码错误：提示账号或密码错误', async ({ page }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)

    await page.goto('/login')
    await page.getByPlaceholder('例如：13800138000').fill(phone)
    await page.getByPlaceholder('至少 6 位字符').fill('wrongpass1')
    await page.locator('button.submitBtn').click()

    await expect(page.getByText('账号或密码错误')).toBeVisible()
  })

  test('账号禁用后登录：提示账号已被禁用', async ({ page, request }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)
    const userId = await page.evaluate(() => localStorage.getItem('userId'))

    // 管理员禁用该账号（设置步骤，走接口）
    await adminLogin(page)
    const adminToken = await page.evaluate(() => localStorage.getItem('token'))
    const dis = await request.put(`/api/admin/users/${userId}/disable?disabled=true`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(dis.status()).toBe(200)

    // 被禁用账号登录应被拒绝
    await page.goto('/login')
    await page.getByPlaceholder('例如：13800138000').fill(phone)
    await page.getByPlaceholder('至少 6 位字符').fill(PASSWORD)
    await page.locator('button.submitBtn').click()

    await expect(page.getByText('账号已被禁用')).toBeVisible()
  })
})

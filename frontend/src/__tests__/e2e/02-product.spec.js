import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, switchUser } from './helpers.js'
import { publishProductViaUI } from './flows.js'
import { findOne } from './db.js'

test.describe('场景2：商品发布与编辑', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('发布商品成功：进入在售状态并落库', async ({ page }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)

    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title, priceYuan: '199', description: '九成新，功能正常' })

    const p = await findOne('SELECT id, title, status, price_cent FROM products WHERE id = ?', [pid])
    expect(p).not.toBeNull()
    expect(p.title).toBe(title)
    expect(p.status).toBe('ON_SALE')
    expect(p.price_cent).toBe(19900)
  })

  test('发布缺少描述：前端校验提示', async ({ page }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)

    await page.goto('/sell')
    await page.getByPlaceholder('例如：二手 iPhone 13，九成新，发票齐全').fill('无描述商品')
    await page.getByPlaceholder('例如：1999.00').fill('100')
    await page.getByRole('button', { name: '发布商品' }).click()

    await expect(page.getByText('请输入描述')).toBeVisible()
  })

  test('编辑自己的商品成功：标题与价格更新并落库', async ({ page }) => {
    const phone = nextPhone()
    await registerViaUI(page, phone)

    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title, priceYuan: '100' })

    await page.goto('/my-products')
    await page.getByRole('button', { name: '编辑' }).first().click()
    await page.locator('.modalCard').getByLabel('标题').fill('更新后的标题')
    await page.locator('.modalCard').getByLabel('价格（元）').fill('250')
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.modalCard')).toBeHidden()

    const p = await findOne('SELECT title, price_cent FROM products WHERE id = ?', [pid])
    expect(p.title).toBe('更新后的标题')
    expect(p.price_cent).toBe(25000)
  })

  test('编辑他人商品：后端 403 权限不足', async ({ page, request }) => {
    const seller = nextPhone()
    const other = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, other)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await switchUser(page, other)
    const token = await page.evaluate(() => localStorage.getItem('token'))
    const resp = await request.put(`/api/products/${pid}`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { title: '恶意篡改', priceCent: 1 },
    })
    expect(resp.status()).toBe(403)
    expect((await resp.json()).error.code).toBe('FORBIDDEN')
  })
})

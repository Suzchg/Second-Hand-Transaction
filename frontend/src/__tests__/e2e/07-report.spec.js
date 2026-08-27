import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, switchUser, adminLogin } from './helpers.js'
import { publishProductViaUI } from './flows.js'
import { findOne } from './db.js'

test.describe('场景7：举报处理（举报+审核）', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('举报违规商品→管理员办结', async ({ page }) => {
    const seller = nextPhone()
    const reporter = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, reporter)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    // 举报人提交举报
    await switchUser(page, reporter)
    await page.goto(`/products/${pid}`)
    await page.getByRole('button', { name: '举报' }).click()
    await page.locator('input[value="COUNTERFEIT"]').check()
    await page.getByPlaceholder('请简要描述举报原因...').fill('疑似假货')
    await page.getByRole('button', { name: '提交举报' }).click()
    await expect(page.getByText('举报已提交')).toBeVisible()

    // 管理员办结（prompt 弹窗）
    await adminLogin(page)
    await page.goto('/admin/reports')
    const row = page.locator('tbody tr').filter({ has: page.getByRole('cell', { name: String(pid), exact: true }) })
    await row.getByRole('button', { name: '处理' }).click()

    await expect.poll(async () => {
      const r = await findOne('SELECT status FROM reports WHERE product_id = ? ORDER BY id DESC LIMIT 1', [pid])
      return r?.status ?? null
    }).toBe('HANDLED')
  })

  test('举报违规商品→管理员驳回', async ({ page }) => {
    const seller = nextPhone()
    const reporter = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, reporter)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await switchUser(page, reporter)
    await page.goto(`/products/${pid}`)
    await page.getByRole('button', { name: '举报' }).click()
    await page.locator('input[value="FALSE_DESC"]').check()
    await page.getByRole('button', { name: '提交举报' }).click()
    await expect(page.getByText('举报已提交')).toBeVisible()

    await adminLogin(page)
    await page.goto('/admin/reports')
    const row = page.locator('tbody tr').filter({ has: page.getByRole('cell', { name: String(pid), exact: true }) })
    await row.getByRole('button', { name: '驳回' }).click()

    await expect.poll(async () => {
      const r = await findOne('SELECT status FROM reports WHERE product_id = ? ORDER BY id DESC LIMIT 1', [pid])
      return r?.status ?? null
    }).toBe('DISMISSED')
  })

  test('举报自己的商品：无举报入口且后端 403', async ({ page, request }) => {
    const seller = nextPhone()
    await registerViaUI(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await page.goto(`/products/${pid}`)
    await expect(page.getByRole('button', { name: '举报' })).toBeHidden()

    const token = await page.evaluate(() => localStorage.getItem('token'))
    const resp = await request.post(`/api/products/${pid}/report`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { reasonType: 'OTHER', description: 'x' },
    })
    expect(resp.status()).toBe(403)
  })
})

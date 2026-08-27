import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, switchUser } from './helpers.js'
import { publishProductViaUI } from './flows.js'
import { findOne } from './db.js'

test.describe('场景5：出价议价（出价+接受/拒绝/撤销）', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('出价→卖家接受→按议价生成订单并支付', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title, priceYuan: '500' })

    // 买家出价 350
    await switchUser(page, buyer)
    await page.goto(`/products/${pid}`)
    await page.getByRole('button', { name: '砍价' }).click()
    await page.getByPlaceholder('例如：1500.00').fill('350')
    await page.getByRole('button', { name: '提交报价' }).click()
    await expect(page.getByText('报价已提交')).toBeVisible()

    // 卖家接受报价
    await switchUser(page, seller)
    await page.goto('/my-orders')
    await page.getByRole('button', { name: '我卖出的' }).click()
    await page.getByRole('button', { name: '接受' }).first().click()

    await expect.poll(async () => {
      const o = await findOne('SELECT status FROM offers WHERE product_id = ? ORDER BY id DESC LIMIT 1', [pid])
      return o?.status ?? null
    }).toBe('ACCEPTED')

    const offer = await findOne('SELECT status, offered_price_cent, order_id FROM offers WHERE product_id = ? ORDER BY id DESC LIMIT 1', [pid])
    expect(offer.offered_price_cent).toBe(35000)
    const oid = offer.order_id
    expect(oid).not.toBeNull()

    // 买家补填收货信息并支付
    await switchUser(page, buyer)
    await page.goto(`/orders/${oid}`)
    await page.getByRole('button', { name: '手动填写' }).click()
    await page.getByPlaceholder('请输入收货人姓名').fill('李四')
    await page.getByPlaceholder('请输入收货人电话').fill('13900000001')
    await page.getByPlaceholder('请输入详细收货地址').fill('测试地址 2 号')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await page.getByRole('button', { name: '去支付' }).click()
    await expect(page.getByText('待发货').first()).toBeVisible()

    const order = await findOne('SELECT amount_cent, status FROM orders WHERE id = ?', [oid])
    expect(order.amount_cent).toBe(35000)
    expect(order.status).toBe('WAIT_DELIVER')
  })

  test('卖家拒绝报价', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await switchUser(page, buyer)
    await page.goto(`/products/${pid}`)
    await page.getByRole('button', { name: '砍价' }).click()
    await page.getByPlaceholder('例如：1500.00').fill('10')
    await page.getByRole('button', { name: '提交报价' }).click()
    await expect(page.getByText('报价已提交')).toBeVisible()

    await switchUser(page, seller)
    await page.goto('/my-orders')
    await page.getByRole('button', { name: '我卖出的' }).click()
    await page.getByRole('button', { name: '拒绝' }).first().click()

    await expect.poll(async () => {
      const o = await findOne('SELECT status FROM offers WHERE product_id = ? ORDER BY id DESC LIMIT 1', [pid])
      return o?.status ?? null
    }).toBe('REJECTED')
  })

  test('买家撤销报价', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await switchUser(page, buyer)
    await page.goto(`/products/${pid}`)
    await page.getByRole('button', { name: '砍价' }).click()
    await page.getByPlaceholder('例如：1500.00').fill('20')
    await page.getByRole('button', { name: '提交报价' }).click()
    await expect(page.getByText('报价已提交')).toBeVisible()

    await page.goto('/my-orders')
    await page.getByRole('button', { name: '取消' }).first().click()

    await expect.poll(async () => {
      const o = await findOne('SELECT status FROM offers WHERE product_id = ? ORDER BY id DESC LIMIT 1', [pid])
      return o?.status ?? null
    }).toBe('CANCELLED')
  })
})

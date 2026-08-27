import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, switchUser, addAddressViaUI } from './helpers.js'
import { publishProductViaUI, buyProductViaUI, payOrderViaUI } from './flows.js'
import { findOne } from './db.js'

test.describe('场景3：商品购买（下单+支付）', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('下单并支付成功：订单进入待发货并落库', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title, priceYuan: '300' })

    await switchUser(page, buyer)
    await addAddressViaUI(page)
    const oid = await buyProductViaUI(page, pid)
    await payOrderViaUI(page, oid)

    const order = await findOne('SELECT id, status, amount_cent FROM orders WHERE id = ?', [oid])
    expect(order).not.toBeNull()
    expect(order.status).toBe('WAIT_DELIVER')
    expect(order.amount_cent).toBe(30000)
  })

  test('购买自己的商品：无「立即购买」入口且后端 403', async ({ page, request }) => {
    const seller = nextPhone()
    await registerViaUI(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await page.goto(`/products/${pid}`)
    await expect(page.getByRole('button', { name: '立即购买' })).toBeHidden()

    const token = await page.evaluate(() => localStorage.getItem('token'))
    const resp = await request.post('/api/orders', {
      headers: { Authorization: `Bearer ${token}` },
      data: { productId: pid, receiverName: '张三', receiverPhone: '13900000000', receiverAddress: '测试地址' },
    })
    expect(resp.status()).toBe(403)
  })
})

import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, switchUser, addAddressViaUI } from './helpers.js'
import { publishProductViaUI, buyProductViaUI, payOrderViaUI, shipOrderViaUI } from './flows.js'
import { findOne } from './db.js'

test.describe('场景4：卖家发货', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('发货成功：生成运单并进入待收货', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await switchUser(page, buyer)
    await addAddressViaUI(page)
    const oid = await buyProductViaUI(page, pid)
    await payOrderViaUI(page, oid)

    await switchUser(page, seller)
    const trackingNo = `SF${oid}`
    await shipOrderViaUI(page, oid, trackingNo)

    const order = await findOne('SELECT status FROM orders WHERE id = ?', [oid])
    expect(order.status).toBe('WAIT_RECEIVE')

    const shipment = await findOne('SELECT tracking_no FROM shipments WHERE order_id = ?', [oid])
    expect(shipment).not.toBeNull()
    expect(shipment.tracking_no).toBe(trackingNo)
  })

  test('未支付订单发货：无发货入口且后端 409', async ({ page, request }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    await switchUser(page, seller)
    const title = `E2E商品-${nextPhone()}`
    const pid = await publishProductViaUI(page, { title })

    await switchUser(page, buyer)
    await addAddressViaUI(page)
    const oid = await buyProductViaUI(page, pid) // 仅下单，未支付

    await switchUser(page, seller)
    await page.goto(`/orders/${oid}`)
    await expect(page.getByRole('button', { name: '发货' })).toBeHidden()

    const token = await page.evaluate(() => localStorage.getItem('token'))
    const resp = await request.post(`/api/orders/${oid}/ship`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { carrierCode: 'SF', trackingNo: 'SF123' },
    })
    expect(resp.status()).toBe(409)
  })
})

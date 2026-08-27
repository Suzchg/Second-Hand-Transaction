import { test, expect } from '@playwright/test'
import { installDialogs, registerViaUI, nextPhone, switchUser, adminLogin } from './helpers.js'
import { createCompletedOrderViaUI } from './flows.js'
import { findOne } from './db.js'

test.describe('场景6：售后处理（申请+审批+仲裁）', () => {
  test.beforeEach(async ({ page }) => installDialogs(page))

  test('退货退款：申请→卖家同意→寄回→确认→退款', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    const { orderId } = await createCompletedOrderViaUI(page, {
      sellerPhone: seller, buyerPhone: buyer, title: `E2E商品-${nextPhone()}`, priceYuan: '200',
    })

    // 买家申请退货退款
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '申请售后' }).click()
    await page.locator('.overlay select').selectOption('RETURN_REFUND')
    await page.getByPlaceholder('请详细描述售后原因...').fill('商品有质量问题')
    await page.getByRole('button', { name: '提交申请' }).click()
    await expect(page.getByText('待卖家处理', { exact: true }).first()).toBeVisible()

    // 卖家同意
    await switchUser(page, seller)
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '同意售后' }).click()
    await expect(page.getByText('已同意退货', { exact: true }).first()).toBeVisible()

    // 买家寄回退货
    await switchUser(page, buyer)
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '填写退货信息' }).click()
    await page.getByPlaceholder('例如：顺丰、圆通、中通').fill('顺丰')
    await page.getByPlaceholder('请输入快递单号').fill(`RT${orderId}`)
    await page.getByRole('button', { name: '确认寄回' }).click()
    await expect(page.getByText('买家已寄回', { exact: true }).first()).toBeVisible()

    // 卖家确认收到退货
    await switchUser(page, seller)
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '确认收到退货' }).click()
    await expect(page.getByText('已退款', { exact: true }).first()).toBeVisible()

    const as = await findOne('SELECT status FROM after_sale_requests WHERE order_id = ?', [orderId])
    expect(as).not.toBeNull()
    expect(as.status).toBe('REFUNDED')
  })

  test('被拒→平台介入→管理员仲裁退款', async ({ page }) => {
    const seller = nextPhone()
    const buyer = nextPhone()
    await registerViaUI(page, seller)
    await registerViaUI(page, buyer)
    const { orderId } = await createCompletedOrderViaUI(page, {
      sellerPhone: seller, buyerPhone: buyer, title: `E2E商品-${nextPhone()}`, priceYuan: '200',
    })

    // 买家申请仅退款（已收货）
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '申请售后' }).click()
    await page.locator('.overlay select').selectOption('REFUND_RECEIVED')
    await page.getByPlaceholder('请详细描述售后原因...').fill('商品与描述不符')
    await page.getByRole('button', { name: '提交申请' }).click()
    await expect(page.getByText('待卖家处理', { exact: true }).first()).toBeVisible()

    // 卖家拒绝
    await switchUser(page, seller)
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '拒绝售后' }).click()
    await page.getByPlaceholder('例如：商品与描述一致，不符合退款条件...').fill('成色属实')
    await page.getByRole('button', { name: '确认拒绝' }).click()
    await expect(page.getByText('已拒绝', { exact: true }).first()).toBeVisible()

    // 买家申请平台介入
    await switchUser(page, buyer)
    await page.goto(`/orders/${orderId}`)
    await page.getByRole('button', { name: '申请平台介入' }).click()
    await page.getByPlaceholder('补充说明或证据材料...').fill('补充证据')
    await page.getByRole('button', { name: '确认申请平台介入' }).click()
    await expect(page.getByText('仲裁中', { exact: true }).first()).toBeVisible()

    // 管理员仲裁：全额退款、卖家责任
    await adminLogin(page)
    await page.goto('/admin/after-sale')
    const card = page.locator('.card').filter({ hasText: `订单 #${orderId}` })
    await card.locator('.row1').click()
    await card.getByPlaceholder('详细说明裁决理由（必填）').fill('支持买家，全额退款')
    await card.getByRole('button', { name: '确认裁决' }).click()
    await expect(card.getByText('已退款', { exact: true }).first()).toBeVisible()

    const as = await findOne('SELECT status, responsibility FROM after_sale_requests WHERE order_id = ?', [orderId])
    expect(as.status).toBe('REFUNDED')
    expect(as.responsibility).toBe('SELLER')
  })
})

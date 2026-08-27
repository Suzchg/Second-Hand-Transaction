import { expect } from '@playwright/test'
import { switchUser, addAddressViaUI, orderIdFromUrl, productIdFromUrl } from './helpers.js'

/**
 * 跨场景复用的业务流（均通过真实浏览器 UI 完成）。
 */

/** 卖家通过 UI 发布商品，返回商品 id */
export async function publishProductViaUI(page, { title, priceYuan = '100', description = '二手闲置，功能正常' } = {}) {
  await page.goto('/sell')
  await page.getByPlaceholder('例如：二手 iPhone 13，九成新，发票齐全').fill(title)
  await page.getByPlaceholder('例如：1999.00').fill(String(priceYuan))
  await page.locator('textarea').fill(description)
  await page.getByRole('button', { name: '发布商品' }).click()
  await page.getByRole('button', { name: '查看商品' }).click()
  await page.waitForURL(/\/products\/\d+/)
  return productIdFromUrl(page.url())
}

/** 买家通过 UI 下单（详情页「立即购买」→ 选地址），返回订单 id */
export async function buyProductViaUI(page, productId) {
  await page.goto(`/products/${productId}`)
  await page.getByRole('button', { name: '立即购买' }).click()
  await page.locator('.backdrop .item').first().click()
  await page.waitForURL(/\/orders\/\d+/)
  return orderIdFromUrl(page.url())
}

/** 买家通过 UI 支付订单，等待状态进入「待发货」 */
export async function payOrderViaUI(page, orderId) {
  await page.goto(`/orders/${orderId}`)
  await page.getByRole('button', { name: '去支付' }).click()
  await expect(page.getByText('待发货').first()).toBeVisible()
}

/** 卖家通过 UI 发货，等待状态进入「待收货」 */
export async function shipOrderViaUI(page, orderId, trackingNo) {
  await page.goto(`/orders/${orderId}`)
  await page.getByRole('button', { name: '发货' }).click()
  await page.getByPlaceholder('例如：SF、YTO、ZTO').fill('SF')
  await page.getByPlaceholder('请输入快递单号').fill(trackingNo)
  await page.getByRole('button', { name: '确认发货' }).click()
  await expect(page.getByText('待收货').first()).toBeVisible()
}

/** 买家通过 UI 确认收货，等待状态进入「已完成」 */
export async function confirmOrderViaUI(page, orderId) {
  await page.goto(`/orders/${orderId}`)
  await page.getByRole('button', { name: '确认收货' }).click()
  await expect(page.getByText('已完成').first()).toBeVisible()
}

/**
 * 走通「发布→下单→支付」，返回 { productId, orderId }（订单处于 WAIT_DELIVER）。
 * 前置：买家/卖家账号已注册（传入手机号）。
 */
export async function createPaidOrderViaUI(page, { sellerPhone, buyerPhone, title, priceYuan }) {
  await switchUser(page, sellerPhone)
  const productId = await publishProductViaUI(page, { title, priceYuan })

  await switchUser(page, buyerPhone)
  await addAddressViaUI(page)
  const orderId = await buyProductViaUI(page, productId)
  await payOrderViaUI(page, orderId)

  return { productId, orderId }
}

/**
 * 走通「发布→下单→支付→发货→确认收货」，返回 { productId, orderId }（订单处于 COMPLETED）。
 */
export async function createCompletedOrderViaUI(page, { sellerPhone, buyerPhone, title, priceYuan }) {
  const { productId, orderId } = await createPaidOrderViaUI(page, { sellerPhone, buyerPhone, title, priceYuan })

  await switchUser(page, sellerPhone)
  await shipOrderViaUI(page, orderId, `SF${orderId}`)

  await switchUser(page, buyerPhone)
  await confirmOrderViaUI(page, orderId)

  return { productId, orderId }
}

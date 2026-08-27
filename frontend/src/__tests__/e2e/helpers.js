import { expect } from '@playwright/test'

/** 统一测试密码 */
export const PASSWORD = 'pass123456'

/** 管理员账号（AdminInitializer 启动自动创建） */
export const ADMIN_PHONE = '13800000000'
export const ADMIN_PASSWORD = 'admin123'

let seq = 0

/**
 * 生成唯一合法手机号（11 位，满足前端 `1[3-9]\d{9}` 校验）。
 * 139 + 6 位时间戳 + 2 位序号，跨用例/跨文件唯一。
 */
export function nextPhone() {
  seq += 1
  const t = String(Date.now()).slice(-6)
  const s = String(seq % 100).padStart(2, '0')
  return `139${t}${s}`
}

/**
 * 统一处理 JS 弹窗：confirm() 视为确定、prompt() 返回备注文本、alert() 关闭。
 * 管理端「处理/驳回/下架/禁用」、订单页「确认退货/取消售后」等均依赖这些弹窗。
 */
export function installDialogs(page) {
  page.on('dialog', (d) => d.accept('e2e备注'))
}

async function waitHome(page) {
  await page.waitForURL((url) => url.pathname === '/', { timeout: 20000 })
}

/** 通过 UI 注册新用户（手机号），成功后自动登录并返回手机号 */
export async function registerViaUI(page, phone = nextPhone()) {
  await page.goto('/login')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await page.getByPlaceholder('例如：13800138000').fill(phone)
  await page.getByPlaceholder('至少 6 位字符').fill(PASSWORD)
  await page.locator('button.submitBtn').click()
  await waitHome(page)
  return phone
}

/** 通过 UI 登录已有账号 */
export async function loginViaUI(page, phone, password = PASSWORD) {
  await page.goto('/login')
  await page.getByPlaceholder('例如：13800138000').fill(phone)
  await page.getByPlaceholder('至少 6 位字符').fill(password)
  await page.locator('button.submitBtn').click()
  await waitHome(page)
}

/** 切换账号：清空 localStorage 后重新登录（模拟「切换账号」） */
export async function switchUser(page, phone, password = PASSWORD) {
  await page.evaluate(() => localStorage.clear())
  await loginViaUI(page, phone, password)
}

/** 切换为管理员登录 */
export async function adminLogin(page) {
  await switchUser(page, ADMIN_PHONE, ADMIN_PASSWORD)
}

/** 选择省/市/区（原生 select 三级联动，无区县数据时手动输入） */
async function fillRegionCascader(page) {
  const selects = page.locator('.cascader select')
  await selects.nth(0).selectOption({ index: 1 })
  await page.waitForFunction(() => {
    const s = document.querySelectorAll('.cascader select')[1]
    return s && !s.disabled && s.options.length > 1
  })
  await selects.nth(1).selectOption({ index: 1 })
  await page.waitForFunction(() => {
    const s = document.querySelectorAll('.cascader select')[2]
    const inp = document.querySelector('.cascader input')
    return (s && s.options.length > 1) || !!inp
  })
  const third = selects.nth(2)
  if (await third.count()) {
    await third.selectOption({ index: 1 })
  } else {
    await page.locator('.cascader input').fill('测试区')
  }
}

/** 通过 UI 新增一个收货地址（购买下单的前置条件） */
export async function addAddressViaUI(page) {
  await page.goto('/profile')
  await page.getByRole('button', { name: '+ 新增地址' }).click()
  await page.waitForURL(/\/profile\/address\/new/)
  await page.getByPlaceholder('姓名').fill('测试收货人')
  await page.getByPlaceholder('手机号').fill('13900000000')
  await fillRegionCascader(page)
  await page.getByPlaceholder('街道/小区/门牌号').fill('测试路 1 号')
  await page.getByRole('button', { name: '保存' }).click()
  await page.waitForURL((url) => url.pathname === '/profile', { timeout: 20000 })
}

/** 从当前 URL 解析订单 id（形如 /orders/123） */
export function orderIdFromUrl(url) {
  const m = url.match(/\/orders\/(\d+)/)
  return m ? Number(m[1]) : null
}

/** 从当前 URL 解析商品 id（形如 /products/123） */
export function productIdFromUrl(url) {
  const m = url.match(/\/products\/(\d+)/)
  return m ? Number(m[1]) : null
}

/** 断言某个元素（含指定文本）在页面上可见，等待出现 */
export async function expectVisible(page, locator) {
  await expect(locator).toBeVisible()
}

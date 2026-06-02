/**
 * HTTP 请求封装。
 *
 * 后端统一返回 ApiResponse<T> { success: boolean, data: T, error?: { code, message } }，
 * 本模块会自动解包：成功时返回 data，失败时抛出带 message 的 Error。
 */

const BASE = ''

export async function api(path, { method = 'GET', body, auth = true, raw = false } = {}) {
  const headers = {}

  // raw body (e.g. FormData) — don't set Content-Type
  if (!raw) {
    headers['Content-Type'] = 'application/json'
  }

  if (auth) {
    const token = localStorage.getItem('token')
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: raw ? body : (body ? JSON.stringify(body) : undefined),
  })

  // 解析响应体
  let json
  try {
    json = await res.json()
  } catch {
    if (!res.ok) {
      throw new Error(res.statusText || '请求失败')
    }
    return null
  }

  // 标准 ApiResponse 格式：{ success, data, error }
  if (json.success === undefined) {
    if (!res.ok) {
      throw new Error(json.message || res.statusText || '请求失败')
    }
    return json
  }

  if (!json.success) {
    const msg = json.error?.message || '请求失败'
    throw new Error(msg)
  }

  return json.data
}

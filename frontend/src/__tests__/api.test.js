import { describe, it, expect, vi, beforeEach } from 'vitest'
import { api } from '../api.js'

/** 构造一个返回指定 JSON 的 fetch mock */
function mockFetch(ok, payload) {
  const fn = vi.fn(async () => ({
    ok,
    status: ok ? 200 : 500,
    statusText: ok ? 'OK' : 'Internal Server Error',
    json: async () => payload,
  }))
  vi.stubGlobal('fetch', fn)
  return fn
}

describe('api 请求封装', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('自动注入 JWT Authorization 头', async () => {
    localStorage.setItem('token', 'test-token')
    const fetchFn = mockFetch(true, { success: true, data: {} })

    await api('/api/foo')

    const [url, opts] = fetchFn.mock.calls[0]
    expect(url).toBe('/api/foo')
    expect(opts.headers.Authorization).toBe('Bearer test-token')
  })

  it('无 token 时不注入 Authorization 头', async () => {
    const fetchFn = mockFetch(true, { success: true, data: {} })

    await api('/api/foo')

    expect(fetchFn.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('auth=false 时即使有 token 也不注入', async () => {
    localStorage.setItem('token', 'test-token')
    const fetchFn = mockFetch(true, { success: true, data: {} })

    await api('/api/foo', { auth: false })

    expect(fetchFn.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('成功时解包返回 data', async () => {
    mockFetch(true, { success: true, data: { id: 1 } })

    const result = await api('/api/foo')

    expect(result).toEqual({ id: 1 })
  })

  it('success=false 时抛出后端错误信息', async () => {
    mockFetch(true, { success: false, error: { code: 'X', message: '业务失败' } })

    await expect(api('/api/foo')).rejects.toThrow('业务失败')
  })

  it('body 对象自动 JSON 序列化', async () => {
    const fetchFn = mockFetch(true, { success: true, data: {} })

    await api('/api/foo', { method: 'POST', body: { a: 1 } })

    expect(fetchFn.mock.calls[0][1].body).toBe(JSON.stringify({ a: 1 }))
  })

  it('raw 模式不设置 Content-Type 且 body 原样传递', async () => {
    const form = new FormData()
    const fetchFn = mockFetch(true, { success: true, data: {} })

    await api('/api/upload', { method: 'POST', raw: true, body: form })

    const opts = fetchFn.mock.calls[0][1]
    expect(opts.headers['Content-Type']).toBeUndefined()
    expect(opts.body).toBe(form)
  })
})

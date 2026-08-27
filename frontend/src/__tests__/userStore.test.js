import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../stores/user.js'

vi.mock('../api.js', () => ({
  api: vi.fn(),
}))

import { api } from '../api.js'

describe('user store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('setAuth 写入状态与 localStorage', () => {
    const store = useUserStore()

    store.setAuth({ accessToken: 'tok', userId: 1, nickname: '小明', role: 'ADMIN', avatarUrl: 'a.png' })

    expect(store.token).toBe('tok')
    expect(store.userId).toBe('1')
    expect(store.nickname).toBe('小明')
    expect(store.role).toBe('ADMIN')
    expect(store.isLoggedIn).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(localStorage.getItem('token')).toBe('tok')
    expect(localStorage.getItem('role')).toBe('ADMIN')
  })

  it('clearAuth 清空状态与 localStorage', () => {
    const store = useUserStore()
    store.setAuth({ accessToken: 'tok', userId: 1, nickname: 'n', role: 'USER', avatarUrl: '' })

    store.clearAuth()

    expect(store.token).toBe('')
    expect(store.userId).toBe('')
    expect(store.isLoggedIn).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('fetchProfile 成功时更新昵称等信息', async () => {
    api.mockResolvedValue({ nickname: '新昵称', role: 'ADMIN', userId: 2, avatarUrl: 'b.png' })
    const store = useUserStore()
    store.token = 'tok'

    await store.fetchProfile()

    expect(store.nickname).toBe('新昵称')
    expect(store.role).toBe('ADMIN')
    expect(store.userId).toBe('2')
    expect(localStorage.getItem('nickname')).toBe('新昵称')
  })

  it('fetchProfile 失败时清除登录态', async () => {
    api.mockRejectedValue(new Error('401'))
    const store = useUserStore()
    store.setAuth({ accessToken: 'tok', userId: 1, nickname: 'n', role: 'USER', avatarUrl: '' })

    await store.fetchProfile()

    expect(store.isLoggedIn).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('无 token 时 fetchProfile 直接返回不请求接口', async () => {
    const store = useUserStore()

    await store.fetchProfile()

    expect(api).not.toHaveBeenCalled()
  })
})

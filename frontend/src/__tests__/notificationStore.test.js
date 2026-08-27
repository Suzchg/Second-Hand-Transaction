import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '../stores/notification.js'

vi.mock('../api.js', () => ({
  api: vi.fn(),
}))

import { api } from '../api.js'

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetch 设置各计数', async () => {
    api.mockResolvedValue({
      unreadMessages: 3,
      pendingOrdersBuyer: 1,
      pendingOrdersSeller: 2,
      pendingOffersReceived: 4,
    })
    const store = useNotificationStore()

    await store.fetch()

    expect(store.unreadMessages).toBe(3)
    expect(store.pendingOrdersBuyer).toBe(1)
    expect(store.pendingOrdersSeller).toBe(2)
    expect(store.pendingOffersReceived).toBe(4)
  })

  it('fetch 接口异常时计数保持不变', async () => {
    api.mockRejectedValue(new Error('boom'))
    const store = useNotificationStore()
    store.unreadMessages = 5

    await store.fetch()

    expect(store.unreadMessages).toBe(5)
  })

  it('pendingTotal 为各待处理订单之和', () => {
    const store = useNotificationStore()
    store.pendingOrdersBuyer = 1
    store.pendingOrdersSeller = 2
    store.pendingOffersReceived = 4

    expect(store.pendingTotal).toBe(7)
  })

  it('startPolling 立即拉取并按间隔轮询', async () => {
    api.mockResolvedValue({ unreadMessages: 0 })
    const store = useNotificationStore()

    store.startPolling(1000)
    expect(api).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(2000)
    expect(api).toHaveBeenCalledTimes(3)

    store.stopPolling()
  })

  it('stopPolling 停止轮询', async () => {
    api.mockResolvedValue({})
    const store = useNotificationStore()

    store.startPolling(1000)
    store.stopPolling()
    await vi.advanceTimersByTimeAsync(5000)

    expect(api).toHaveBeenCalledTimes(1)
  })
})

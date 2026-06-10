import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api.js'

/**
 * 通知 Store。
 * 管理未读消息数、待处理订单数，提供轮询和手动刷新。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadMessages = ref(0)
  const pendingOrdersBuyer = ref(0)
  const pendingOrdersSeller = ref(0)
  const pendingOffersReceived = ref(0)

  const pendingTotal = computed(() =>
    pendingOrdersBuyer.value + pendingOrdersSeller.value + pendingOffersReceived.value
  )

  let timer = null

  async function fetch() {
    try {
      const n = await api('/api/users/notifications')
      unreadMessages.value = n.unreadMessages || 0
      pendingOrdersBuyer.value = n.pendingOrdersBuyer || 0
      pendingOrdersSeller.value = n.pendingOrdersSeller || 0
      pendingOffersReceived.value = n.pendingOffersReceived || 0
    } catch { /* ignore */ }
  }

  function startPolling(intervalMs = 60000) {
    stopPolling()
    fetch()
    timer = setInterval(fetch, intervalMs)
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  return {
    unreadMessages, pendingOrdersBuyer, pendingOrdersSeller, pendingOffersReceived,
    pendingTotal,
    fetch, startPolling, stopPolling,
  }
})

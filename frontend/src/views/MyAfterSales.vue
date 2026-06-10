<script setup>
import { onMounted, ref, computed } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'

const router = useRouter()
const tab = ref('buyer')
const loading = ref(true)
const error = ref('')
const items = ref([])

async function load() {
  loading.value = true; error.value = ''
  try {
    const endpoint = tab.value === 'buyer' ? '/api/after-sale/my-requests' : '/api/after-sale/my-received'
    items.value = await api(endpoint) || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally { loading.value = false }
}

function goOrder(item) {
  router.push(`/orders/${item.orderId}`)
}

function typeLabel(t) {
  const m = { REFUND_NOT_SHIPPED: '仅退款（未发货）', REFUND_RECEIVED: '仅退款（已收货）', RETURN_REFUND: '退货退款', PARTIAL_REFUND: '部分退款' }
  return m[t] || t
}

function statusLabel(s) {
  const m = { REQUESTED: '待卖家处理', APPROVED: '已同意退货', REJECTED: '已拒绝', RETURN_SHIPPED: '买家已寄回', RETURN_CONFIRMED: '已确认退货', REFUNDED: '已退款', CLOSED: '已关闭', PLATFORM_ARBITRATION: '仲裁中' }
  return m[s] || s
}

function statusCls(s) {
  const m = { REQUESTED: 'warn', APPROVED: 'ok', REJECTED: 'err', RETURN_SHIPPED: 'info', RETURN_CONFIRMED: 'ok', REFUNDED: 'ok', CLOSED: 'dim', PLATFORM_ARBITRATION: 'active' }
  return m[s] || 'dim'
}

function deadlineLabel(t) {
  if (!t) return ''
  const d = new Date(t).getTime() - Date.now()
  if (d <= 0) return '已超时'
  const days = Math.floor(d / 86400000)
  const hours = Math.floor((d % 86400000) / 3600000)
  return days > 0 ? `剩余 ${days} 天` : `剩余 ${hours} 小时`
}

const emptyText = computed(() => tab.value === 'buyer' ? '暂无发起的售后' : '暂无收到的售后')

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="pageHead">
      <h2>售后记录</h2>
      <button class="refreshBtn" @click="load">刷新</button>
    </div>

    <!-- Tabs -->
    <div class="tabRow">
      <button :class="['tabBtn', { active: tab === 'buyer' }]" @click="tab = 'buyer'; load()">我发起的</button>
      <button :class="['tabBtn', { active: tab === 'seller' }]" @click="tab = 'seller'; load()">我收到的</button>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="errMsg">{{ error }}</p>

    <div v-else-if="items.length" class="list">
      <div v-for="r in items" :key="r.id" class="card" @click="goOrder(r)">
        <div class="row1">
          <span class="asId">售后 #{{ r.id }}</span>
          <span class="orderLink">订单 #{{ r.orderId }}</span>
          <span :class="['tag', statusCls(r.status)]">{{ statusLabel(r.status) }}</span>
          <span class="typeTag">{{ typeLabel(r.requestType) }}</span>
          <span class="amount">¥{{ ((r.refundAmountCent || 0) / 100).toFixed(2) }}</span>
        </div>
        <div class="row2">
          <span>{{ r.reason || '无原因描述' }}</span>
          <span class="time">{{ r.createdAt?.substring(0, 16) }}</span>
          <span v-if="r.deadlineAt" class="deadline" :class="{ overdue: deadlineLabel(r.deadlineAt) === '已超时' }">
            ⏱ {{ deadlineLabel(r.deadlineAt) }}
          </span>
        </div>
      </div>
    </div>

    <p v-else class="empty">{{ emptyText }}</p>
  </div>
</template>

<style scoped>
.page { max-width: 640px; margin: 0 auto; }

.pageHead { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-lg); }
h2 { margin: 0; font-size: 20px; font-weight: 700; }

.refreshBtn {
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 6px 14px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* Tabs */
.tabRow {
  display: flex; gap: 4px; margin-bottom: var(--space-lg);
  background: var(--bg-primary); border: 1px solid var(--border-light);
  border-radius: var(--radius-lg); padding: 4px; width: fit-content;
}
.tabBtn {
  border: none; background: transparent; padding: 8px 20px;
  border-radius: var(--radius-md); cursor: pointer; font-size: 13px;
  color: var(--text-tertiary); font-weight: 500;
}
.tabBtn.active {
  background: var(--brand-gradient); color: white; font-weight: 600;
  box-shadow: var(--shadow-brand);
}

/* List */
.list { display: grid; gap: var(--space-sm); }
.card {
  background: var(--bg-primary); border: 1px solid var(--border-light);
  border-radius: var(--radius-lg); padding: var(--space-lg); cursor: pointer;
  display: grid; gap: 6px; transition: all var(--transition-fast);
}
.card:hover { border-color: var(--border-strong); box-shadow: var(--shadow-sm); }

.row1 { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.row2 { display: flex; gap: 10px; align-items: center; font-size: 12px; color: var(--text-tertiary); }

.asId { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.orderLink { font-size: 12px; color: var(--info); }
.amount { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.typeTag { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: var(--bg-secondary); color: var(--text-secondary); }
.time { font-size: 11px; color: var(--text-disabled); }
.deadline { font-size: 11px; color: var(--warning); }
.deadline.overdue { color: var(--error); }

/* Tags */
.tag { font-size: 11px; padding: 2px 8px; border-radius: 6px; font-weight: 500; }
.tag.warn { background: var(--warning-bg); color: var(--warning); }
.tag.ok { background: var(--success-bg); color: var(--success); }
.tag.err { background: var(--error-bg); color: var(--error); }
.tag.dim { background: var(--bg-secondary); color: var(--text-tertiary); }
.tag.info { background: var(--info-bg); color: var(--info); }
.tag.active { background: #f5f3ff; color: #7c3aed; }

.muted { color: var(--text-tertiary); font-size: 13px; }
.errMsg { color: var(--error); font-size: 13px; }
.empty { text-align: center; padding: 40px; color: var(--text-tertiary); font-size: 13px; }
</style>

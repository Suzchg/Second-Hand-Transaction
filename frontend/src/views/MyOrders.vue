<script setup>
import { onMounted, ref, computed } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'

const router = useRouter()
const tab = ref('buy')  // 'buy' | 'sell'
const loading = ref(true)
const error = ref('')
const items = ref([])
const acceptingId = ref(null)

// ---- Data loading ----

async function load() {
  loading.value = true; error.value = ''
  try {
    if (tab.value === 'buy') {
      const [offers, orders] = await Promise.all([
        api('/api/my-offers'),
        api('/api/orders/bought?size=50'),
      ])
      // Merge offers and orders, mark type
      const merged = [
        ...(offers || []).map(o => ({ ...o, _type: 'offer' })),
        ...((orders || {}).content || []).map(o => ({ ...o, _type: 'order' })),
      ]
      items.value = merged.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
    } else {
      const [offers, orders] = await Promise.all([
        api('/api/seller-offers'),
        api('/api/orders/sold?size=50'),
      ])
      const merged = [
        ...(offers || []).map(o => ({ ...o, _type: 'offer' })),
        ...((orders || {}).content || []).map(o => ({ ...o, _type: 'order' })),
      ]
      items.value = merged.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
    }
    // Fetch product titles
    const ids = [...new Set(items.value.map(i => i.productId).filter(Boolean))]
    const titles = {}
    await Promise.all(ids.map(async id => {
      try { const prod = await api(`/api/products/${id}`, { auth: false }); titles[id] = prod.title }
      catch { titles[id] = `商品 #${id}` }
    }))
    items.value.forEach(i => i._title = titles[i.productId] || '')
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally { loading.value = false }
}

function label(item) {
  if (item._type === 'offer') {
    const m = { PENDING: '待处理', ACCEPTED: '已接受', REJECTED: '已拒绝', CANCELLED: '已取消' }
    const c = { PENDING: 'warn', ACCEPTED: 'ok', REJECTED: 'err', CANCELLED: 'dim' }
    return { tag: '报价', label: m[item.status] || item.status, cls: c[item.status] || 'dim' }
  }
  const m = { WAIT_PAY: '待支付', WAIT_DELIVER: '待发货', WAIT_RECEIVE: '待收货', COMPLETED: '已完成', CANCELLED: '已取消', AFTER_SALE: '售后中' }
  const c = { WAIT_PAY: 'warn', WAIT_DELIVER: 'info', WAIT_RECEIVE: 'active', COMPLETED: 'ok', CANCELLED: 'dim', AFTER_SALE: 'warn' }
  return { tag: '订单', label: m[item.status] || item.status, cls: c[item.status] || 'dim' }
}

// ---- Actions ----

async function cancelOffer(item) {
  if (!confirm('确定取消？')) return
  try { await api(`/api/offers/${item.id}/cancel`, { method: 'POST' }); await load() }
  catch (e) { alert(e.message) }
}

async function acceptOffer(item) {
  acceptingId.value = item.id
  try {
    const order = await api(`/api/offers/${item.id}/accept`, { method: 'POST' })
    alert(`已接受！订单 #${order.id} 已创建`)
    await load()
  } catch (e) { alert(e.message) }
  finally { acceptingId.value = null }
}

async function rejectOffer(item) {
  if (!confirm('确定拒绝？')) return
  try { await api(`/api/offers/${item.id}/reject`, { method: 'POST' }); await load() }
  catch (e) { alert(e.message) }
}

function goOrder(item) {
  // For offers that have been accepted and have an orderId
  if (item._type === 'offer' && item.orderId) return router.push(`/orders/${item.orderId}`)
  // For orders
  if (item._type === 'order') return router.push(`/orders/${item.id}`)
}

// ---- Computed ----

const filtered = computed(() => {
  return items.value.filter(i => {
    if (i.status === 'COMPLETED' || i.status === 'CANCELLED') return false
    if (i.status === 'REJECTED') return false
    // 已接受的报价：已被订单替代，直接隐藏
    if (i._type === 'offer' && i.status === 'ACCEPTED') return false
    return true
  })
})

const emptyText = computed(() => tab.value === 'buy' ? '暂无进行中的购买或报价' : '暂无进行中的售出或报价')

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>订单</h2>
      <button class="ghost" @click="load">刷新</button>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button :class="['tab', { active: tab === 'buy' }]" @click="tab = 'buy'; load()">我买到的</button>
      <button :class="['tab', { active: tab === 'sell' }]" @click="tab = 'sell'; load()">我卖出的</button>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <div v-else-if="filtered.length" class="list">
      <div
        v-for="item in filtered" :key="`${item._type}-${item.id}`"
        class="card"
        :class="{ clickable: item._type === 'order' || (item._type === 'offer' && item.orderId) }"
        @click="goOrder(item)"
      >
        <div class="topRow">
          <span class="typeTag">{{ label(item).tag }}</span>
          <span class="productTitle">{{ item._title || `商品 #${item.productId}` }}</span>
          <span :class="['statusTag', label(item).cls]">{{ label(item).label }}</span>
        </div>

        <div class="infoRow">
          <span v-if="item._type === 'offer'">
            报价 <strong>¥{{ (item.offeredPriceCent / 100).toFixed(2) }}</strong>
          </span>
          <span v-else>
            <strong>¥{{ (item.amountCent / 100).toFixed(2) }}</strong>
          </span>
          <span v-if="item.message" class="msg">"{{ item.message }}"</span>
        </div>

        <div class="actionRow">
          <span class="time">{{ item.createdAt?.substring(0, 16) }}</span>

          <!-- 我买到的：报价 -->
          <template v-if="tab === 'buy' && item._type === 'offer'">
            <button v-if="item.status === 'PENDING'" class="link" @click.stop="cancelOffer(item)">取消</button>
            <button v-if="item.status === 'ACCEPTED' && item.orderId" class="link primary" @click.stop="goOrder(item)">去支付</button>
          </template>

          <!-- 我买到的：订单 -->
          <template v-if="tab === 'buy' && item._type === 'order'">
            <span v-if="item.status === 'WAIT_PAY'" class="hint">去支付</span>
            <span v-else-if="item.status === 'WAIT_DELIVER'" class="hint dim">等待卖家发货</span>
            <span v-else-if="item.status === 'WAIT_RECEIVE'" class="hint">确认收货</span>
          </template>

          <!-- 我卖出的：报价 -->
          <template v-if="tab === 'sell' && item._type === 'offer' && item.status === 'PENDING'">
            <button class="btn accept" :disabled="acceptingId === item.id" @click.stop="acceptOffer(item)">
              {{ acceptingId === item.id ? '...' : '接受' }}
            </button>
            <button class="btn reject" @click.stop="rejectOffer(item)">拒绝</button>
          </template>
          <!-- 我卖出的：报价已接受 -->
          <template v-if="tab === 'sell' && item._type === 'offer' && item.status === 'ACCEPTED' && item.orderId">
            <span class="link dim" @click.stop="goOrder(item)">→ 订单 #{{ item.orderId }}</span>
          </template>

          <!-- 我卖出的：订单 -->
          <template v-if="tab === 'sell' && item._type === 'order'">
            <span v-if="item.status === 'WAIT_DELIVER'" class="hint">去发货</span>
            <span v-else-if="item.status === 'WAIT_RECEIVE'" class="hint dim">等待买家收货</span>
          </template>
        </div>
      </div>
    </div>

    <p v-else class="muted">{{ emptyText }}</p>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
h2 { margin: 0; font-size: 20px; }
.ghost { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; }

.tabs { display: flex; gap: 0; margin-bottom: 14px; border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; overflow: hidden; width: fit-content; }
.tab {
  border: 0; background: white; padding: 8px 20px; cursor: pointer; font-size: 13px;
  color: rgba(0,0,0,0.55); transition: all 0.12s;
}
.tab:first-child { border-right: 1px solid rgba(0,0,0,0.1); }
.tab.active { background: black; color: white; }

.list { display: grid; gap: 10px; max-width: 640px; }
.card {
  border: 1px solid rgba(0,0,0,0.08); border-radius: 16px;
  padding: 14px 16px; background: white;
}
.card.clickable { cursor: pointer; transition: background 0.12s; }
.card.clickable:hover { background: rgba(0,0,0,0.01); }

.topRow { display: flex; align-items: center; gap: 8px; }
.typeTag {
  font-size: 11px; padding: 1px 6px; border-radius: 4px;
  background: rgba(0,0,0,0.06); color: rgba(0,0,0,0.45); flex-shrink: 0;
}
.productTitle { font-weight: 600; font-size: 14px; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.statusTag {
  display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 12px; font-weight: 500; flex-shrink: 0;
}
.statusTag.warn { background: #fff3e0; color: #e65100; }
.statusTag.ok { background: #e8f5e9; color: #2e7d32; }
.statusTag.err { background: #fce4ec; color: #b00020; }
.statusTag.dim { background: rgba(0,0,0,0.06); color: rgba(0,0,0,0.4); }
.statusTag.info { background: #e3f2fd; color: #1565c0; }
.statusTag.active { background: #e8f5e9; color: #2e7d32; }

.infoRow { margin-top: 6px; display: flex; gap: 10px; align-items: center; font-size: 14px; }
.msg { color: rgba(0,0,0,0.4); font-size: 13px; }

.actionRow { margin-top: 8px; display: flex; gap: 10px; align-items: center; }
.time { font-size: 12px; color: rgba(0,0,0,0.35); flex: 1; }
.hint { font-size: 12px; color: #1565c0; font-weight: 500; }
.hint.dim { color: rgba(0,0,0,0.35); font-weight: 400; }

.link { background: none; border: 0; color: rgba(0,0,0,0.55); cursor: pointer; font-size: 13px; padding: 0; }
.link:hover { color: rgba(0,0,0,0.8); }
.link.primary { color: #1565c0; font-weight: 600; }
.link.dim { color: rgba(0,0,0,0.4); cursor: pointer; }

.btn {
  border: 0; padding: 6px 14px; border-radius: 8px; cursor: pointer;
  font-size: 13px; font-weight: 500;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn.accept { background: black; color: white; }
.btn.reject { background: rgba(0,0,0,0.08); color: rgba(0,0,0,0.65); }

.muted { color: rgba(0,0,0,0.45); font-size: 13px; }
.error { color: #b00020; }
</style>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'
import { toast } from '../toast.js'

const router = useRouter()
const tab = ref('buy')
const loading = ref(true)
const error = ref('')
const items = ref([])
const acceptingId = ref(null)

async function load() {
  loading.value = true; error.value = ''
  try {
    if (tab.value === 'buy') {
      const [offers, orders] = await Promise.all([
        api('/api/my-offers'),
        api('/api/orders/bought?size=50'),
      ])
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
    // Fetch product info
    const ids = [...new Set(items.value.map(i => i.productId).filter(Boolean))]
    const products = {}
    await Promise.all(ids.map(async id => {
      try { products[id] = await api(`/api/products/${id}`, { auth: false }) }
      catch { products[id] = { title: `商品 #${id}`, coverImageUrl: '', priceCent: 0 } }
    }))
    items.value.forEach(i => {
      const prod = products[i.productId] || {}
      i._title = prod.title || ''
      i._cover = prod.coverImageUrl || ''
      i._price = prod.priceCent || 0
    })
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
  const m = { WAIT_PAY: '待支付', WAIT_DELIVER: '待发货', WAIT_RECEIVE: '待收货', COMPLETED: '已完成', CANCELLED: '已取消', AFTER_SALE: '售后中', SETTLED: '已结算' }
  const c = { WAIT_PAY: 'warn', WAIT_DELIVER: 'info', WAIT_RECEIVE: 'active', COMPLETED: 'ok', CANCELLED: 'dim', AFTER_SALE: 'warn', SETTLED: 'ok' }
  return { tag: '订单', label: m[item.status] || item.status, cls: c[item.status] || 'dim' }
}

function notifyDone() { window.dispatchEvent(new CustomEvent('action-done')) }

async function cancelOffer(item) {
  if (!confirm('确定取消？')) return
  try { await api(`/api/offers/${item.id}/cancel`, { method: 'POST' }); await load(); notifyDone() }
  catch (e) { alert(e.message) }
}

async function acceptOffer(item) {
  acceptingId.value = item.id
  try {
    const order = await api(`/api/offers/${item.id}/accept`, { method: 'POST' })
    alert(`已接受！订单 #${order.id} 已创建`)
    await load()
    notifyDone()
  } catch (e) { alert(e.message) }
  finally { acceptingId.value = null }
}

async function rejectOffer(item) {
  if (!confirm('确定拒绝？')) return
  try { await api(`/api/offers/${item.id}/reject`, { method: 'POST' }); await load(); notifyDone() }
  catch (e) { alert(e.message) }
}

function goOrder(item) {
  if (item._type === 'offer' && item.orderId) return router.push(`/orders/${item.orderId}`)
  if (item._type === 'order') return router.push(`/orders/${item.id}`)
}

const filtered = computed(() => {
  return items.value.filter(i => {
    if (i.status === 'REJECTED') return false
    if (i._type === 'offer' && i.status === 'ACCEPTED') return false
    return true
  })
})

const emptyText = computed(() => tab.value === 'buy' ? '暂无购买记录' : '暂无售出记录')

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="pageHead">
      <h2><AppIcon name="clipboard" :size="20"/> 订单</h2>
      <button class="refreshBtn" @click="load">刷新</button>
    </div>

    <!-- Tabs -->
    <div class="tabRow">
      <button :class="['tabBtn', { active: tab === 'buy' }]" @click="tab = 'buy'; load()">我买到的</button>
      <button :class="['tabBtn', { active: tab === 'sell' }]" @click="tab = 'sell'; load()">我卖出的</button>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="errMsg">{{ error }}</p>

    <div v-else-if="filtered.length" class="orderList">
      <div
        v-for="item in filtered" :key="`${item._type}-${item.id}`"
        class="orderCard"
        :class="{ clickable: item._type === 'order' || (item._type === 'offer' && item.orderId) }"
        @click="goOrder(item)"
      >
        <div class="orderInner">
          <!-- 商品缩略图 -->
          <div
            class="orderThumb"
            :style="item._cover ? { backgroundImage: `url(${item._cover})` } : {}"
            @click.stop="item._type === 'order' ? router.push(`/orders/${item.id}`) : router.push(`/products/${item.productId}`)"
          >
            <span v-if="!item._cover" class="thumbFallback"><AppIcon name="image" :size="24"/></span>
          </div>

          <div class="orderBody">
            <div class="orderTop">
              <span class="typeTag">{{ label(item).tag }}</span>
              <span class="orderTitle">{{ item._title || `商品 #${item.productId}` }}</span>
              <span :class="['statusTag', label(item).cls]">{{ label(item).label }}</span>
            </div>

            <div class="orderInfo">
              <span v-if="item._type === 'offer'">
                报价 <strong>¥{{ (item.offeredPriceCent / 100).toFixed(2) }}</strong>
              </span>
              <span v-else>
                成交 <strong>¥{{ (item.amountCent / 100).toFixed(2) }}</strong>
              </span>
              <span v-if="item.message" class="orderMsg">"{{ item.message }}"</span>
            </div>

            <div v-if="item._type === 'order' && item.receiverName" class="orderReceiver">
              {{ tab === 'sell' ? '买家' : '收货人' }}：{{ item.receiverName }} {{ item.receiverPhone }}
            </div>

            <div class="orderActions">
              <span class="orderTime">{{ item.createdAt?.substring(0, 16) }}</span>

              <!-- 买到的：报价 -->
              <template v-if="tab === 'buy' && item._type === 'offer'">
                <button v-if="item.status === 'PENDING'" class="actionLink" @click.stop="cancelOffer(item)">取消</button>
                <button v-if="item.status === 'ACCEPTED' && item.orderId" class="actionLink primary" @click.stop="goOrder(item)">去支付</button>
              </template>

              <!-- 买到的：订单提示 -->
              <template v-if="tab === 'buy' && item._type === 'order'">
                <span v-if="item.status === 'WAIT_PAY'" class="hintText">去支付 →</span>
                <span v-else-if="item.status === 'WAIT_DELIVER'" class="hintText dim">等待发货</span>
                <span v-else-if="item.status === 'WAIT_RECEIVE'" class="hintText">确认收货</span>
              </template>

              <!-- 卖出的：报价 -->
              <template v-if="tab === 'sell' && item._type === 'offer' && item.status === 'PENDING'">
                <button class="btnAction accept" :disabled="acceptingId === item.id" @click.stop="acceptOffer(item)">
                  {{ acceptingId === item.id ? '...' : '接受' }}
                </button>
                <button class="btnAction reject" @click.stop="rejectOffer(item)">拒绝</button>
              </template>

              <template v-if="tab === 'sell' && item._type === 'offer' && item.status === 'ACCEPTED' && item.orderId">
                <span class="actionLink dim" @click.stop="goOrder(item)">→ 订单 #{{ item.orderId }}</span>
              </template>

              <!-- 卖出的：订单 -->
              <template v-if="tab === 'sell' && item._type === 'order'">
                <span v-if="item.status === 'WAIT_DELIVER'" class="hintText">去发货 →</span>
                <span v-else-if="item.status === 'WAIT_RECEIVE'" class="hintText dim">等待买家收货</span>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <p v-else class="emptyState">{{ emptyText }}</p>
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

.refreshBtn:hover { border-color: var(--border-strong); color: var(--text-secondary); }

/* Tabs */
.tabRow {
  display: flex;
  gap: 4px;
  margin-bottom: var(--space-lg);
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 4px;
  width: fit-content;
}

.tabBtn {
  border: none;
  background: transparent;
  padding: 8px 20px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-tertiary);
  font-weight: 500;
  transition: all var(--transition-fast);
}

.tabBtn.active {
  background: var(--brand-gradient);
  color: white;
  font-weight: 600;
  box-shadow: var(--shadow-brand);
}

.tabBtn:hover:not(.active) { color: var(--text-secondary); }

/* 订单列表 */
.orderList { display: grid; gap: var(--space-sm); }

.orderCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  transition: all var(--transition-fast);
}

.orderCard.clickable { cursor: pointer; }
.orderCard.clickable:hover { border-color: var(--border-strong); box-shadow: var(--shadow-sm); }

.orderInner { display: flex; gap: var(--space-md); align-items: flex-start; }

.orderThumb {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumbFallback { display: flex; align-items: center; justify-content: center; opacity: 0.3; }

.orderBody { flex: 1; min-width: 0; display: grid; gap: var(--space-xs); }

.orderTop { display: flex; align-items: center; gap: var(--space-sm); }

.typeTag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.orderTitle {
  font-weight: 600;
  font-size: 14px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.statusTag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.statusTag.warn { background: var(--warning-bg); color: var(--warning); }
.statusTag.ok { background: var(--success-bg); color: var(--success); }
.statusTag.err { background: var(--error-bg); color: var(--error); }
.statusTag.dim { background: var(--bg-secondary); color: var(--text-tertiary); }
.statusTag.info { background: var(--info-bg); color: var(--info); }
.statusTag.active { background: var(--success-bg); color: var(--success); }

.orderInfo { font-size: 14px; color: var(--text-secondary); display: flex; gap: var(--space-sm); align-items: center; }

.orderInfo strong { color: var(--text-primary); }

.orderMsg {
  font-size: 13px;
  color: var(--text-tertiary);
  font-style: italic;
}

.orderReceiver { font-size: 12px; color: var(--text-tertiary); }

.orderActions { display: flex; gap: var(--space-sm); align-items: center; margin-top: var(--space-xs); }

.orderTime {
  font-size: 11px;
  color: var(--text-disabled);
  flex: 1;
}

.hintText { font-size: 12px; color: var(--brand-darker); font-weight: 600; }
.hintText.dim { color: var(--text-tertiary); font-weight: 400; }

.actionLink {
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.actionLink:hover { color: var(--text-primary); }
.actionLink.primary { color: var(--info); font-weight: 600; }
.actionLink.primary:hover { color: #4096ff; }
.actionLink.dim { color: var(--text-disabled); cursor: pointer; }

.btnAction {
  border: none;
  padding: 6px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.btnAction.accept { background: var(--brand-gradient); color: white; }
.btnAction.accept:hover { opacity: 0.9; }
.btnAction.accept:disabled { opacity: 0.5; cursor: not-allowed; }

.btnAction.reject {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.btnAction.reject:hover { background: var(--border-light); }

.muted { color: var(--text-tertiary); font-size: 13px; }
.errMsg { color: var(--error); font-size: 13px; }

.emptyState { text-align: center; padding: 40px; color: var(--text-tertiary); font-size: 13px; }
</style>

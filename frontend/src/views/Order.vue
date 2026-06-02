<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'
import { useRoute } from 'vue-router'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const detail = ref(null)
const tracking = ref(null)

async function load() {
  loading.value = true; error.value = ''
  try { detail.value = await api(`/api/orders/${route.params.id}`) }
  catch (e) { error.value = e.message || '加载失败' }
  finally { loading.value = false }
}
async function pay() { try { await api(`/api/orders/${route.params.id}/pay`, { method: 'POST' }); await load() } catch (e) { alert(e.message) } }
async function confirm() { try { await api(`/api/orders/${route.params.id}/confirm`, { method: 'POST' }); await load() } catch (e) { alert(e.message) } }
async function cancel() { try { await api(`/api/orders/${route.params.id}/cancel`, { method: 'POST' }); await load() } catch (e) { alert(e.message) } }
async function ship() {
  const code = prompt('快递公司（如 SF、YTO）'); if (!code) return
  const no = prompt('快递单号'); if (!no) return
  try { await api(`/api/orders/${route.params.id}/ship`, { method: 'POST', body: { carrierCode: code, trackingNo: no } }); await load() } catch (e) { alert(e.message) }
}
async function afterSale() {
  const type = prompt('售后类型：REFUND（仅退款）或 RETURN_REFUND（退货退款）', 'REFUND'); if (!type) return
  const reason = prompt('申请原因'); if (!reason) return
  try { await api('/api/after-sale', { method: 'POST', body: { orderId: detail.value.order.id, type, reason } }); alert('售后申请已提交'); await load() } catch (e) { alert(e.message) }
}
async function loadTracking() { try { tracking.value = await api(`/api/shipments/${route.params.id}/track`) } catch (e) { alert(e.message) } }

onMounted(load)
</script>

<template>
  <button class="back" @click="$router.back()">← 返回</button>
  <p v-if="loading" class="muted">加载中...</p>
  <p v-else-if="error" class="error">{{ error }}</p>

  <div v-else class="wrap">
    <div class="card">
      <h3>订单 #{{ detail.order.id }}</h3>
      <div class="infoGrid">
        <div><span class="label">状态</span><span class="val status">{{ detail.order.status }}</span></div>
        <div><span class="label">金额</span><span class="val">¥{{ (detail.order.amountCent / 100).toFixed(2) }}</span></div>
        <div><span class="label">创建</span><span class="val">{{ detail.order.createdAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.paidAt"><span class="label">支付</span><span class="val">{{ detail.order.paidAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.shippedAt"><span class="label">发货</span><span class="val">{{ detail.order.shippedAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.completedAt"><span class="label">完成</span><span class="val">{{ detail.order.completedAt?.substring(0,16) }}</span></div>
      </div>
    </div>

    <div class="card">
      <h3>收货信息</h3>
      <div class="infoGrid">
        <div><span class="label">收货人</span><span class="val">{{ detail.order.receiverName }}</span></div>
        <div><span class="label">电话</span><span class="val">{{ detail.order.receiverPhone }}</span></div>
        <div><span class="label">地址</span><span class="val">{{ detail.order.receiverAddress }}</span></div>
      </div>
    </div>

    <div v-if="detail.shipment" class="card">
      <h3>物流信息</h3>
      <div class="infoGrid">
        <div><span class="label">快递</span><span class="val">{{ detail.shipment.carrierCode }}</span></div>
        <div><span class="label">单号</span><span class="val">{{ detail.shipment.trackingNo }}</span></div>
        <div><span class="label">状态</span><span class="val">{{ detail.shipment.status }}</span></div>
      </div>
      <button class="btn sm" @click="loadTracking">查物流</button>
      <div v-if="tracking" class="trackCard">
        <div v-for="t in tracking.points" :key="t.time" class="trackItem">
          <div class="trackTime">{{ t.time?.substring(0,16) }}</div>
          <div class="trackDesc">{{ t.desc }}</div>
        </div>
      </div>
    </div>

    <div class="actions">
      <button class="btn" @click="load">刷新</button>
      <button v-if="detail.canPay" class="btn primary" @click="pay">💰 去支付</button>
      <button v-if="detail.canShip" class="btn primary" @click="ship">📦 发货</button>
      <button v-if="detail.canConfirm" class="btn primary" @click="confirm">✅ 确认收货</button>
      <button v-if="detail.canCancel" class="btn danger" @click="cancel">取消订单</button>
      <button v-if="detail.canApplyAfterSale" class="btn warn" @click="afterSale">申请售后</button>
    </div>

    <div v-if="detail.events?.length" class="card">
      <h3>状态时间线</h3>
      <div v-for="e in detail.events" :key="e.id" class="eventItem">
        <div class="eventTime">{{ e.createdAt?.substring(0,16) }}</div>
        <div class="eventDot" />
        <div class="eventNote">{{ e.note }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.back { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; }
.wrap { margin-top: 12px; display: grid; gap: 12px; max-width: 600px; }
.card { border: 1px solid rgba(0,0,0,0.08); border-radius: 16px; padding: 16px; background: white; }
h3 { margin: 0 0 10px; font-size: 15px; }
.infoGrid { display: grid; gap: 6px; }
.label { font-size: 12px; color: rgba(0,0,0,0.4); margin-right: 8px; }
.val { font-size: 14px; }
.val.status { font-weight: 600; color: #1565c0; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.btn { padding: 8px 14px; border: 1px solid rgba(0,0,0,0.12); border-radius: 10px; background: white; cursor: pointer; font-size: 13px; }
.btn.sm { padding: 5px 10px; font-size: 12px; margin-top: 6px; }
.btn.primary { background: black; color: white; border-color: black; }
.btn.danger { color: #b00020; border-color: #b00020; }
.btn.warn { color: #e65100; border-color: #e65100; }
.trackCard { margin-top: 8px; padding: 10px; background: rgba(0,0,0,0.02); border-radius: 10px; }
.trackItem { display: flex; gap: 10px; padding: 3px 0; font-size: 13px; }
.trackTime { color: rgba(0,0,0,0.35); white-space: nowrap; min-width: 120px; }
.trackDesc { color: rgba(0,0,0,0.65); }
.eventItem { display: flex; align-items: center; gap: 10px; padding: 5px 0; font-size: 13px; }
.eventTime { color: rgba(0,0,0,0.35); min-width: 120px; }
.eventDot { width: 8px; height: 8px; border-radius: 50%; background: rgba(0,0,0,0.25); flex-shrink: 0; }
.eventNote { color: rgba(0,0,0,0.7); }
.muted { color: rgba(0,0,0,0.45); }
.error { color: #b00020; }
</style>

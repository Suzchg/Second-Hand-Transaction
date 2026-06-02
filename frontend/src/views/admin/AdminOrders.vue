<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api.js'

const orders = ref({ content: [] })
const msg = ref('')

async function load(p = 0) {
  try { orders.value = await api(`/api/admin/orders?page=${p}&size=20`) }
  catch (e) { msg.value = e.message }
}

async function markPaid(o) {
  if (!confirm(`确定标记订单 #${o.id} 为已支付？`)) return
  try { await api(`/api/admin/orders/${o.id}/mark-paid`, { method: 'POST' }); msg.value = '已标记支付'; await load(orders.value.number) }
  catch (e) { msg.value = e.message }
}
async function cancelOrder(o) {
  if (!confirm(`确定取消订单 #${o.id}？`)) return
  try { await api(`/api/admin/orders/${o.id}/cancel`, { method: 'POST' }); msg.value = '已取消'; await load(orders.value.number) }
  catch (e) { msg.value = e.message }
}

const statusLabel = {
  WAIT_PAY: '待支付', WAIT_DELIVER: '待发货', WAIT_RECEIVE: '待收货',
  COMPLETED: '已完成', CANCELLED: '已取消', AFTER_SALE: '售后中',
}

onMounted(() => load())
</script>

<template>
  <div>
    <h2>订单管理</h2>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <table>
      <thead><tr><th>ID</th><th>买家</th><th>卖家</th><th>金额</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="o in orders.content" :key="o.id">
          <td>#{{ o.id }}</td>
          <td>{{ o.buyerId }}</td>
          <td>{{ o.sellerId }}</td>
          <td>¥{{ (o.amountCent / 100).toFixed(2) }}</td>
          <td><span :class="['tag', o.status]">{{ statusLabel[o.status] || o.status }}</span></td>
          <td class="time">{{ o.createdAt?.substring(0,16) }}</td>
          <td class="actions">
            <button v-if="o.status === 'WAIT_PAY'" class="act" @click="markPaid(o)">标记支付</button>
            <button v-if="o.status !== 'COMPLETED' && o.status !== 'CANCELLED'" class="act danger" @click="cancelOrder(o)">取消</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager" v-if="orders.totalPages > 1">
      <button :disabled="orders.first" @click="load(orders.number - 1)">上一页</button>
      <span>{{ orders.number + 1 }} / {{ orders.totalPages }}</span>
      <button :disabled="orders.last" @click="load(orders.number + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
h2 { margin: 0 0 12px; font-size: 20px; }
.msg { padding: 6px 10px; background: #e8f5e9; border-radius: 8px; font-size: 13px; color: #2e7d32; margin-bottom: 8px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px 10px; text-align: left; border-bottom: 1px solid rgba(0,0,0,0.05); }
th { font-weight: 600; color: rgba(0,0,0,0.5); font-size: 12px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 11px; background: rgba(0,0,0,0.06); }
.tag.WAIT_PAY { background: #fff3e0; color: #e65100; }
.tag.WAIT_DELIVER { background: #e3f2fd; color: #1565c0; }
.tag.WAIT_RECEIVE { background: #e8eaf6; color: #283593; }
.tag.COMPLETED { background: #e8f5e9; color: #2e7d32; }
.tag.CANCELLED { background: #fce4ec; color: #b00020; }
.tag.AFTER_SALE { background: #fce4ec; color: #c62828; }
.time { color: rgba(0,0,0,0.4); white-space: nowrap; }
.actions { display: flex; gap: 6px; }
.act { padding: 4px 8px; border: 1px solid rgba(0,0,0,0.12); border-radius: 6px; background: white; cursor: pointer; font-size: 11px; }
.act.danger { color: #b00020; border-color: #b00020; }
.pager { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 13px; }
.pager button { padding: 6px 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; background: white; cursor: pointer; }
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>

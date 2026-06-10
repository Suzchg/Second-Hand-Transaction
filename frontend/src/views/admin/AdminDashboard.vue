<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api.js'

const stats = ref(null)

onMounted(async () => {
  try { stats.value = await api('/api/admin/dashboard') }
  catch (e) { console.error(e) }
})

const orderStatusLabel = {
  WAIT_PAY: '待支付', WAIT_DELIVER: '待发货', WAIT_RECEIVE: '待收货',
  COMPLETED: '已完成', CANCELLED: '已取消', AFTER_SALE: '售后中',
}
</script>

<template>
  <div>
    <h2>数据总览</h2>
    <p v-if="!stats" class="muted">加载中...</p>
    <div v-else>
      <div class="cards">
        <div class="card"><div class="num">{{ stats.totalUsers }}</div><div class="label">总用户</div></div>
        <div class="card"><div class="num">{{ stats.activeUsers }}</div><div class="label">在线用户</div></div>
        <div class="card"><div class="num">{{ stats.totalProducts }}</div><div class="label">总商品</div></div>
        <div class="card"><div class="num">{{ stats.onSaleProducts }}</div><div class="label">在售商品</div></div>
        <div class="card"><div class="num">{{ stats.totalOrders }}</div><div class="label">总订单</div></div>
        <div class="card"><div class="num">{{ stats.todayNewUsers }}</div><div class="label">今日新用户</div></div>
        <div class="card"><div class="num">{{ stats.todayNewOrders }}</div><div class="label">今日新订单</div></div>
      </div>

      <h3 style="margin-top:24px">订单状态分布</h3>
      <div class="statusList" v-if="stats.orderStatusDistribution?.length">
        <div v-for="s in stats.orderStatusDistribution" :key="s.status" class="statusItem">
          <span class="statusName">{{ orderStatusLabel[s.status] || s.status }}</span>
          <span class="statusBar"><span class="fill" :style="{ width: (s.count / stats.totalOrders * 100).toFixed(1) + '%' }" /></span>
          <span class="statusCount">{{ s.count }}</span>
        </div>
      </div>
      <p v-else class="muted">暂无订单数据</p>
    </div>
  </div>
</template>

<style scoped>
h2 { margin: 0 0 16px; font-size: 20px; color: var(--text-primary); }
h3 { margin: 0 0 10px; font-size: 15px; color: var(--text-primary); }
.muted { color: var(--text-tertiary); }
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
@media (max-width: 900px) { .cards { grid-template-columns: repeat(2, 1fr); } }
.card {
  border: 1px solid var(--border-light); border-radius: 14px;
  padding: 18px; text-align: center; background: var(--bg-primary);
}
.num { font-size: 28px; font-weight: 700; color: var(--text-primary); }
.label { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
.statusList { display: grid; gap: 8px; }
.statusItem { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.statusName { min-width: 60px; color: var(--text-secondary); }
.statusBar { flex: 1; height: 8px; background: var(--bg-secondary); border-radius: 4px; overflow: hidden; }
.fill { display: block; height: 100%; background: var(--brand); border-radius: 4px; min-width: 2px; }
.statusCount { font-weight: 600; min-width: 30px; text-align: right; color: var(--text-primary); }
</style>

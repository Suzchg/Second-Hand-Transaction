<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api.js'

const reports = ref({ content: [] })
const statusFilter = ref('')
const msg = ref('')

const reasonLabels = {
  COUNTERFEIT: '假冒伪劣', PROHIBITED: '违禁物品', FALSE_DESC: '虚假描述',
  PRICE_FRAUD: '价格欺诈', PRIVACY: '侵犯隐私', OTHER: '其他',
}
const statusLabels = {
  PENDING: '待处理', HANDLED: '已处理', DISMISSED: '已驳回',
}

async function load(p = 0) {
  try {
    let url = `/api/admin/reports?page=${p}&size=20`
    if (statusFilter.value) url += `&status=${statusFilter.value}`
    reports.value = await api(url)
  } catch (e) { msg.value = e.message }
}

async function handleReport(r) {
  const note = prompt('处理备注（选填）：')
  if (note === null) return // cancelled
  try {
    await api(`/api/admin/reports/${r.id}/handle`, {
      method: 'PUT',
      body: { handleNote: note || null },
    })
    msg.value = '已处理'
    await load(reports.value.number)
  } catch (e) { msg.value = e.message }
}

async function delistAndHandle(r) {
  if (!confirm(`确定下架商品 #${r.productId} 并处理此举报？`)) return
  try {
    await api(`/api/admin/products/${r.productId}/off-shelf`, { method: 'PUT' })
    await api(`/api/admin/reports/${r.id}/handle`, {
      method: 'PUT',
      body: { handleNote: '已下架商品' },
    })
    msg.value = '已下架商品并处理举报'
    await load(reports.value.number)
  } catch (e) { msg.value = e.message }
}

async function dismissReport(r) {
  const note = prompt('驳回备注（选填）：')
  if (note === null) return
  try {
    await api(`/api/admin/reports/${r.id}/dismiss`, {
      method: 'PUT',
      body: { handleNote: note || null },
    })
    msg.value = '已驳回'
    await load(reports.value.number)
  } catch (e) { msg.value = e.message }
}

onMounted(() => load())
</script>

<template>
  <div>
    <h2>举报管理</h2>
    <div class="bar">
      <select v-model="statusFilter" @change="load()" class="sel">
        <option value="">全部状态</option>
        <option value="PENDING">待处理</option>
        <option value="HANDLED">已处理</option>
        <option value="DISMISSED">已驳回</option>
      </select>
      <button class="btn" @click="load()">刷新</button>
    </div>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>商品ID</th>
          <th>举报人ID</th>
          <th>原因</th>
          <th>描述</th>
          <th>状态</th>
          <th>时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in reports.content" :key="r.id">
          <td>{{ r.id }}</td>
          <td>{{ r.productId }}</td>
          <td>{{ r.reporterId }}</td>
          <td>{{ reasonLabels[r.reasonType] || r.reasonType }}</td>
          <td class="descCell">{{ r.description || '-' }}</td>
          <td>
            <span :class="['tag', r.status === 'PENDING' ? 'pending' : r.status === 'HANDLED' ? 'handled' : 'dismissed']">
              {{ statusLabels[r.status] || r.status }}
            </span>
          </td>
          <td class="time">{{ r.createdAt?.substring(0, 16) }}</td>
          <td class="actions">
            <template v-if="r.status === 'PENDING'">
              <button class="act" @click="handleReport(r)">处理</button>
              <button class="act warn" @click="delistAndHandle(r)">下架</button>
              <button class="act danger" @click="dismissReport(r)">驳回</button>
            </template>
            <span v-else class="muted">
              {{ r.handledAt?.substring(0, 10) }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager" v-if="reports.totalPages > 1">
      <button :disabled="reports.first" @click="load(reports.number - 1)">上一页</button>
      <span>{{ reports.number + 1 }} / {{ reports.totalPages }}</span>
      <button :disabled="reports.last" @click="load(reports.number + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
h2 { margin: 0 0 12px; font-size: 20px; }
.bar { display: flex; gap: 8px; margin-bottom: 10px; }
.sel { padding: 7px 10px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; outline: none; background: white; }
.btn { padding: 7px 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; background: white; cursor: pointer; }
.msg { padding: 6px 10px; background: #e8f5e9; border-radius: 8px; font-size: 13px; color: #2e7d32; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px 10px; text-align: left; border-bottom: 1px solid rgba(0,0,0,0.05); }
th { font-weight: 600; color: rgba(0,0,0,0.5); font-size: 12px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 11px; background: rgba(0,0,0,0.06); }
.tag.pending { background: #fff3e0; color: #e65100; }
.tag.handled { background: #e8f5e9; color: #2e7d32; }
.tag.dismissed { background: #fce4ec; color: #b00020; }
.time { color: rgba(0,0,0,0.4); white-space: nowrap; font-size: 12px; }
.descCell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.actions { display: flex; gap: 6px; }
.act {
  padding: 4px 8px; border: 1px solid rgba(0,0,0,0.12); border-radius: 6px;
  background: white; cursor: pointer; font-size: 11px;
}
.act.danger { color: #b00020; border-color: #b00020; }
.act.warn { color: #e65100; border-color: #e65100; }
.muted { color: rgba(0,0,0,0.35); font-size: 11px; }
.pager { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 13px; }
.pager button { padding: 6px 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; background: white; cursor: pointer; }
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api.js'

const products = ref({ content: [] })
const keyword = ref('')
const msg = ref('')

async function load(p = 0) {
  try {
    let url = `/api/admin/products?page=${p}&size=20`
    if (keyword.value) url += `&keyword=${encodeURIComponent(keyword.value)}`
    products.value = await api(url)
  } catch (e) { msg.value = e.message }
}

async function offShelf(p) {
  if (!confirm(`确定下架「${p.title}」？`)) return
  try { await api(`/api/admin/products/${p.id}/off-shelf`, { method: 'PUT' }); msg.value = '已下架'; await load(products.value.number) }
  catch (e) { msg.value = e.message }
}
async function onShelf(p) {
  try { await api(`/api/admin/products/${p.id}/on-shelf`, { method: 'PUT' }); msg.value = '已上架'; await load(products.value.number) }
  catch (e) { msg.value = e.message }
}
async function del(p) {
  if (!confirm(`确定删除「${p.title}」？此操作不可恢复！`)) return
  try { await api(`/api/admin/products/${p.id}`, { method: 'DELETE' }); msg.value = '已删除'; await load(products.value.number) }
  catch (e) { msg.value = e.message }
}

onMounted(() => load())
</script>

<template>
  <div>
    <h2>商品管理</h2>
    <div class="bar">
      <input v-model="keyword" placeholder="搜索商品..." @keyup.enter="load()" />
      <button class="btn" @click="load()">搜索</button>
    </div>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <table>
      <thead><tr><th>ID</th><th>标题</th><th>价格</th><th>状态</th><th>卖家ID</th><th>发布时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="p in products.content" :key="p.id">
          <td>{{ p.id }}</td>
          <td>{{ p.title?.substring(0, 30) }}</td>
          <td>¥{{ (p.priceCent / 100).toFixed(2) }}</td>
          <td><span :class="p.status === 'ON_SALE' ? 'tag on' : p.status === 'OFF_SALE' ? 'tag off' : 'tag'">
            {{ p.status === 'ON_SALE' ? '在售' : p.status === 'OFF_SALE' ? '已下架' : p.status }}
          </span></td>
          <td>{{ p.sellerId }}</td>
          <td class="time">{{ p.createdAt?.substring(0,10) }}</td>
          <td class="actions">
            <button v-if="p.status === 'ON_SALE'" class="act" @click="offShelf(p)">下架</button>
            <button v-if="p.status === 'OFF_SALE'" class="act" @click="onShelf(p)">上架</button>
            <button class="act danger" @click="del(p)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager" v-if="products.totalPages > 1">
      <button :disabled="products.first" @click="load(products.number - 1)">上一页</button>
      <span>{{ products.number + 1 }} / {{ products.totalPages }}</span>
      <button :disabled="products.last" @click="load(products.number + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
h2 { margin: 0 0 12px; font-size: 20px; color: var(--text-primary); }
.bar { display: flex; gap: 8px; margin-bottom: 10px; }
.bar input {
  padding: 7px 10px; border: 1px solid var(--border-default); border-radius: 8px;
  outline: none; width: 200px; background: var(--bg-primary); color: var(--text-primary);
}
.btn {
  padding: 7px 12px; border: 1px solid var(--border-default); border-radius: 8px;
  background: var(--bg-primary); cursor: pointer; color: var(--text-secondary);
}
.msg { padding: 6px 10px; background: var(--success-bg); border-radius: 8px; font-size: 13px; color: var(--success); }
table { width: 100%; border-collapse: collapse; font-size: 13px; color: var(--text-primary); }
th, td { padding: 8px 10px; text-align: left; border-bottom: 1px solid var(--border-light); }
th { font-weight: 600; color: var(--text-tertiary); font-size: 12px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 11px; background: var(--bg-secondary); color: var(--text-secondary); }
.tag.on { background: var(--success-bg); color: var(--success); }
.tag.off { background: var(--error-bg); color: var(--error); }
.time { color: var(--text-tertiary); white-space: nowrap; }
.actions { display: flex; gap: 6px; }
.act {
  padding: 4px 8px; border: 1px solid var(--border-default); border-radius: 6px;
  background: var(--bg-primary); cursor: pointer; font-size: 11px; color: var(--text-secondary);
}
.act.danger { color: var(--error); border-color: var(--error-border); }
.pager { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 13px; color: var(--text-primary); }
.pager button {
  padding: 6px 12px; border: 1px solid var(--border-default); border-radius: 8px;
  background: var(--bg-primary); cursor: pointer; color: var(--text-secondary);
}
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>

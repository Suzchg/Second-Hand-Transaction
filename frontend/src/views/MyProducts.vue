<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const loading = ref(true)
const error = ref('')
const items = ref([])

// ---- Edit modal ----
const editing = ref(null)
const editForm = ref({ title: '', priceYuan: '', description: '', quantity: 1 })
const editError = ref('')
const editSaving = ref(false)

function statusInfo(p) {
  if (p.status === 'ON_SALE') return { label: '在售', cls: 'ON_SALE' }
  return { label: '已下架', cls: 'OFF_SALE' }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const page = await api('/api/my-products?page=0&size=50')
    // 只保留有库存的（售罄的移到已售出）
    items.value = (page.content || []).filter(p => p.quantity && p.quantity > 0)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function daysOnSale(createdAt) {
  if (!createdAt) return '-'
  return Math.floor((Date.now() - new Date(createdAt)) / 86400000)
}

async function offShelf(p) {
  try {
    await api(`/api/products/${p.id}`, { method: 'PUT', body: { status: 'OFF_SALE' } })
    await load()
  } catch (e) { alert(e.message || '操作失败') }
}

async function onShelf(p) {
  try {
    await api(`/api/products/${p.id}`, { method: 'PUT', body: { status: 'ON_SALE' } })
    await load()
  } catch (e) { alert(e.message || '操作失败') }
}

function openEdit(p) {
  editing.value = p.id
  editForm.value = {
    title: p.title,
    priceYuan: (p.priceCent / 100).toFixed(2),
    description: p.description || '',
    quantity: p.quantity || 1,
  }
  editError.value = ''
}

function cancelEdit() { editing.value = null }

async function saveEdit(p) {
  editError.value = ''
  const priceCent = Math.round(Number(editForm.value.priceYuan || '0') * 100)
  if (!editForm.value.title.trim()) { editError.value = '标题不能为空'; return }
  if (!priceCent || priceCent <= 0) { editError.value = '价格无效'; return }
  editSaving.value = true
  try {
    await api(`/api/products/${p.id}`, {
      method: 'PUT',
      body: {
        title: editForm.value.title.trim(),
        priceCent,
        description: editForm.value.description.trim(),
        quantity: editForm.value.quantity,
      },
    })
    editing.value = null
    await load()
  } catch (e) {
    editError.value = e.message || '保存失败'
  } finally {
    editSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>在售</h2>
      <button class="ghost" @click="load">刷新</button>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <table v-else-if="items.length" class="table">
      <thead>
        <tr>
          <th>商品</th>
          <th>价格</th>
          <th>库存</th>
          <th>状态</th>
          <th>在售天数</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in items" :key="p.id">
          <td>
            <router-link :to="`/products/${p.id}`" class="title">{{ p.title }}</router-link>
          </td>
          <td class="price">¥{{ (p.priceCent / 100).toFixed(2) }}</td>
          <td class="qty">{{ p.quantity ?? 0 }}</td>
          <td>
            <span :class="['tag', statusInfo(p).cls]">{{ statusInfo(p).label }}</span>
          </td>
          <td class="days">{{ daysOnSale(p.createdAt) }} 天</td>
          <td class="actions">
            <button class="link" @click="openEdit(p)">编辑</button>
            <button v-if="p.status === 'ON_SALE'" class="link" @click="offShelf(p)">下架</button>
            <button v-else class="link" @click="onShelf(p)">上架</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else class="muted">暂无商品，<router-link to="/sell">去发布</router-link></p>

    <!-- Edit overlay -->
    <div v-if="editing" class="overlay" @click.self="cancelEdit">
      <div class="modal">
        <h3>编辑商品</h3>
        <label>标题 <input v-model="editForm.title" /></label>
        <label>价格（元）<input v-model="editForm.priceYuan" type="number" step="0.01" min="0" /></label>
        <label>库存 <input v-model.number="editForm.quantity" type="number" min="1" max="999" /></label>
        <label>描述 <textarea v-model="editForm.description" rows="3" /></label>
        <p v-if="editError" class="error">{{ editError }}</p>
        <div class="modalActions">
          <button class="primary" :disabled="editSaving" @click="saveEdit(items.find(i => i.id === editing))">
            {{ editSaving ? '保存中...' : '保存' }}
          </button>
          <button class="ghost" @click="cancelEdit">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
h2 { margin: 0; font-size: 20px; }
.ghost {
  border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 6px 10px; border-radius: 10px; cursor: pointer;
}
.table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 10px 8px; border-bottom: 1px solid rgba(0,0,0,0.08); color: rgba(0,0,0,0.5); font-weight: 500; font-size: 12px; }
td { padding: 10px 8px; border-bottom: 1px solid rgba(0,0,0,0.04); }
.title { font-weight: 500; color: rgba(0,0,0,0.85); text-decoration: none; }
.title:hover { color: #1565c0; text-decoration: underline; }
.price { font-weight: 600; }
.qty { color: rgba(0,0,0,0.5); font-size: 13px; }
.days { color: rgba(0,0,0,0.5); font-size: 13px; }
.actions { display: flex; gap: 8px; align-items: center; }
.tag {
  display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 12px; font-weight: 500;
}
.tag.ON_SALE { background: #e8f5e9; color: #2e7d32; }
.tag.OFF_SALE { background: rgba(0,0,0,0.06); color: rgba(0,0,0,0.5); }
.tag.SOLD_OUT { background: #fce4ec; color: #b00020; }
.link {
  background: none; border: 0; color: rgba(0,0,0,0.55); cursor: pointer; font-size: 13px; padding: 0;
}
.link:hover { color: rgba(0,0,0,0.8); }

/* Modal */
.overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.35);
  display: flex; align-items: center; justify-content: center; z-index: 50;
}
.modal {
  background: white; border-radius: 18px; padding: 22px;
  width: 100%; max-width: 420px;
  display: grid; gap: 12px;
}
.modal h3 { margin: 0; font-size: 18px; }
.modal label { display: grid; gap: 4px; font-size: 13px; color: rgba(0,0,0,0.7); }
.modal input, .modal textarea {
  border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  padding: 8px 10px; outline: none; font-size: 14px; font-family: inherit;
}
.modal textarea { resize: vertical; min-height: 60px; }
.modalActions { display: flex; gap: 8px; }
.primary {
  border: 0; background: black; color: white;
  padding: 10px 14px; border-radius: 12px; cursor: pointer;
}
.primary:disabled { opacity: 0.5; cursor: not-allowed; }
.muted { color: rgba(0,0,0,0.45); font-size: 13px; }
.error { color: #b00020; font-size: 13px; margin: 0; }
</style>

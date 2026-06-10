<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'
import ImageUploader from '../components/ImageUploader.vue'

const loading = ref(true)
const error = ref('')
const items = ref([])

// ---- Edit modal ----
const editing = ref(null)
const editForm = ref({ title: '', priceYuan: '', description: '', quantity: 1, categoryId: null, condition: null, freeShipping: false, shippingFeeYuan: '' })
const editError = ref('')
const editSaving = ref(false)
const categories = ref([])
const editingProductId = ref(null)

function statusInfo(p) {
  if (p.status === 'ON_SALE') return { label: '在售', cls: 'ON_SALE' }
  return { label: '已下架', cls: 'OFF_SALE' }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const page = await api('/api/my-products?page=0&size=50')
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

async function openEdit(p) {
  editing.value = p.id
  editingProductId.value = p.id
  editForm.value = {
    title: p.title,
    priceYuan: (p.priceCent / 100).toFixed(2),
    description: p.description || '',
    quantity: p.quantity || 1,
    categoryId: p.categoryId,
    condition: p.condition,
    freeShipping: p.freeShipping || false,
    shippingFeeYuan: ((p.shippingFeeCent || 0) / 100).toFixed(2),
  }
  editError.value = ''
  if (categories.value.length === 0) {
    try { categories.value = await api('/api/categories', { auth: false }) || [] }
    catch { /* ignore */ }
  }
}

function cancelEdit() { editing.value = null; editingProductId.value = null }

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
        categoryId: editForm.value.categoryId,
        condition: editForm.value.condition,
        freeShipping: editForm.value.freeShipping === true,
        shippingFeeCent: editForm.value.freeShipping ? 0 : Math.round(Number(editForm.value.shippingFeeYuan || '0') * 100),
      },
    })
    editing.value = null
    editingProductId.value = null
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
  <div class="page">
    <div class="pageHead">
      <h2><AppIcon name="package" :size="20"/> 我在售的</h2>
      <button class="refreshBtn" @click="load">刷新</button>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="errMsg">{{ error }}</p>

    <div v-else-if="items.length" class="productGrid">
      <div v-for="p in items" :key="p.id" class="productCard">
        <!-- 封面图 -->
        <div
          class="cardCover"
          v-lazy-bg="p.coverImageUrl || ''"
          @click="$router.push(`/products/${p.id}`)"
        >
          <span v-if="!p.coverImageUrl" class="coverFallback"><AppIcon name="image" :size="36"/></span>
        </div>

        <!-- 信息 -->
        <div class="cardInfo">
          <router-link :to="`/products/${p.id}`" class="cardTitle">{{ p.title }}</router-link>
          <div class="cardMeta">
            <span class="cardPrice">¥{{ (p.priceCent / 100).toFixed(2) }}</span>
            <span class="cardQty">库存 {{ p.quantity ?? 0 }}</span>
            <span :class="['stockTag', statusInfo(p).cls]">{{ statusInfo(p).label }}</span>
          </div>
          <div class="cardFooter">
            <span class="cardDays">在售 {{ daysOnSale(p.createdAt) }} 天</span>
            <span class="cardActions">
              <button class="cardLink" @click="openEdit(p)">编辑</button>
              <button v-if="p.status === 'ON_SALE'" class="cardLink" @click="offShelf(p)">下架</button>
              <button v-else class="cardLink" @click="onShelf(p)">上架</button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <p v-else class="emptyState">暂无商品，<router-link to="/sell">去发布</router-link></p>

    <!-- 编辑弹窗 -->
    <Teleport to="body">
      <div v-if="editing" class="modalOverlay" @click.self="cancelEdit">
        <div class="modalCard">
          <h3>编辑商品</h3>
          <div class="modalForm">
            <label>标题 <input v-model="editForm.title" /></label>
            <label>分类
              <select v-model="editForm.categoryId" class="selInput">
                <option :value="null">请选择分类（可选）</option>
                <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.iconUrl }} {{ c.name }}</option>
              </select>
            </label>
            <div class="formRow">
              <label>价格（元）<input v-model="editForm.priceYuan" type="number" step="0.01" min="0" /></label>
              <label>库存 <input v-model.number="editForm.quantity" type="number" min="1" max="999" /></label>
            </div>
            <label>成色
              <select v-model="editForm.condition" class="selInput">
                <option :value="null">请选择成色（可选）</option>
                <option value="NEW">全新</option>
                <option value="LIKE_NEW">99新</option>
                <option value="NINE_TENTHS">9成新</option>
                <option value="EIGHT_TENTHS">8成新</option>
                <option value="SEVEN_TENTHS">7成新</option>
                <option value="SIX_TENTHS_AND_BELOW">6成新及以下</option>
              </select>
            </label>
            <label>描述 <textarea v-model="editForm.description" rows="3" /></label>
            <label class="checkLabel">
              <input type="checkbox" v-model="editForm.freeShipping" />
              <span>包邮（免邮费）</span>
            </label>
            <label v-if="!editForm.freeShipping">邮费（元）<input v-model="editForm.shippingFeeYuan" type="number" step="1" min="0" /></label>
            <div class="imageSection">
              <span class="imageLabel">商品图片</span>
              <ImageUploader :productId="editingProductId" />
            </div>
          </div>
          <p v-if="editError" class="fieldErr">{{ editError }}</p>
          <div class="modalActions">
            <button class="btnPrimary" :disabled="editSaving" @click="saveEdit(items.find(i => i.id === editing))">
              {{ editSaving ? '保存中...' : '保存' }}
            </button>
            <button class="btnGhost" @click="cancelEdit">取消</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.page { max-width: 680px; margin: 0 auto; }

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

/* 商品网格 */
.productGrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
}

@media (min-width: 760px) { .productGrid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }

.productCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-normal);
}

.productCard:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }

.cardCover {
  height: 160px;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.coverFallback { display: flex; align-items: center; justify-content: center; opacity: 0.3; }

.cardInfo { padding: var(--space-md); display: grid; gap: var(--space-sm); }

.cardTitle {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  text-decoration: none;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cardTitle:hover { color: var(--brand-darker); }

.cardMeta { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; }

.cardPrice { font-weight: 700; font-size: 15px; color: var(--accent); }

.cardQty { font-size: 11px; color: var(--text-tertiary); }

.stockTag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-full);
  font-size: 10px;
  font-weight: 600;
}

.stockTag.ON_SALE { background: var(--success-bg); color: var(--success); }
.stockTag.OFF_SALE { background: var(--bg-secondary); color: var(--text-tertiary); }

.cardFooter { display: flex; justify-content: space-between; align-items: center; }

.cardDays { font-size: 11px; color: var(--text-disabled); }

.cardActions { display: flex; gap: var(--space-sm); }

.cardLink {
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 12px;
  padding: 0;
  transition: color var(--transition-fast);
}

.cardLink:hover { color: var(--text-primary); }

.muted { color: var(--text-tertiary); font-size: 13px; }
.errMsg { color: var(--error); font-size: 13px; }

.emptyState { text-align: center; padding: 40px; color: var(--text-tertiary); font-size: 13px; }
.emptyState a { color: var(--brand-darker); }

/* 弹窗 */
.modalOverlay {
  position: fixed;
  inset: 0;
  z-index: 150;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-xl);
  animation: fadeIn 0.2s ease;
}

.modalCard {
  background: var(--bg-primary);
  border-radius: var(--radius-xl);
  padding: var(--space-2xl);
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-xl);
  animation: scaleIn 0.25s ease;
}

.modalCard h3 { margin: 0; font-size: 18px; }

.modalForm { display: grid; gap: var(--space-md); margin-top: var(--space-lg); }

.modalForm label {
  display: grid;
  gap: var(--space-xs);
  font-size: 13px;
  color: var(--text-secondary);
}

.modalForm input, .modalForm textarea, .selInput {
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 9px 12px;
  font-size: 14px;
  font-family: inherit;
  background: var(--bg-tertiary);
}

.modalForm textarea { resize: vertical; min-height: 60px; }

.selInput { background: var(--bg-tertiary); cursor: pointer; }

.formRow {
  display: grid;
  grid-template-columns: 1fr 100px;
  gap: var(--space-md);
}

.checkLabel { display: flex !important; align-items: center; gap: var(--space-sm) !important; cursor: pointer; }
.checkLabel input[type="checkbox"] { width: 16px; height: 16px; accent-color: var(--brand-dark); }

.imageSection { display: grid; gap: var(--space-xs); }
.imageLabel { font-size: 13px; color: var(--text-secondary); }

.fieldErr { color: var(--error); font-size: 13px; margin: 0; }

.modalActions { display: flex; gap: var(--space-sm); margin-top: var(--space-lg); }

.btnPrimary {
  flex: 1;
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 11px 20px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
}

.btnPrimary:disabled { opacity: 0.5; cursor: not-allowed; }

.btnGhost {
  border: 1.5px solid var(--border-default);
  background: var(--bg-primary);
  padding: 11px 20px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
}

.btnGhost:hover { border-color: var(--border-strong); }
</style>

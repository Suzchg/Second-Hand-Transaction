<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const items = ref([])

onMounted(async () => {
  try {
    const page = await api('/api/users/favorites?size=50')
    const favs = page.content || []
    if (!favs.length) { loading.value = false; return }

    const ids = [...new Set(favs.map(f => f.productId))]
    const products = {}
    await Promise.all(ids.map(async id => {
      try { products[id] = await api(`/api/products/${id}`, { auth: false }) }
      catch { products[id] = { title: `商品 #${id}`, priceCent: 0, coverImageUrl: '' } }
    }))
    items.value = favs.map(f => ({
      ...f,
      _product: products[f.productId] || { title: '未知', priceCent: 0, coverImageUrl: '' }
    }))
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="head">
      <button class="backBtn" @click="router.push('/profile')">← 返回</button>
      <h2><AppIcon name="heart" :size="20"/> 我的收藏</h2>
      <span />
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="errMsg">{{ error }}</p>
    <p v-else-if="!items.length" class="emptyText">暂无收藏</p>

    <div v-else class="favGrid">
      <div
        v-for="item in items" :key="item.id"
        class="favCard" @click="router.push(`/products/${item.productId}`)"
      >
        <div
          class="favCover"
          v-lazy-bg="item._product.coverImageUrl || ''"
        >
          <span v-if="!item._product.coverImageUrl" class="coverFallback"><AppIcon name="image" :size="36"/></span>
        </div>
        <div class="favInfo">
          <div class="favTitle">{{ item._product.title }}</div>
          <div class="favPrice">¥{{ ((item._product.priceCent || 0) / 100).toFixed(2) }}</div>
          <div class="favTime">收藏于 {{ item.createdAt?.substring(0, 10) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 680px; margin: 0 auto; }

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

h2 { margin: 0; font-size: 20px; font-weight: 700; }

.backBtn {
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 7px 14px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.backBtn:hover { border-color: var(--border-strong); color: var(--text-primary); }

.favGrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
}

@media (min-width: 760px) { .favGrid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (min-width: 960px) { .favGrid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }

.favCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.favCard:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.favCover {
  height: 160px;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
}

.coverFallback { display: flex; align-items: center; justify-content: center; opacity: 0.3; }

.favInfo { padding: var(--space-md); display: grid; gap: 4px; }

.favTitle {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.favPrice { font-weight: 700; font-size: 15px; color: var(--accent); }

.favTime { font-size: 11px; color: var(--text-tertiary); }

.muted { color: var(--text-tertiary); font-size: 13px; }
.errMsg { color: var(--error); }
.emptyText { text-align: center; padding: 40px; color: var(--text-tertiary); font-size: 13px; }
</style>

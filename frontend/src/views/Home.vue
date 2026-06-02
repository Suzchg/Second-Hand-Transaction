<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'
import CategoryNav from '../components/CategoryNav.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const items = ref([])
const selectedCategory = ref(null)
const keyword = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    let url = '/api/products?page=0&size=20'
    if (selectedCategory.value) url += `&categoryId=${selectedCategory.value}`
    if (keyword.value.trim()) url += `&keyword=${encodeURIComponent(keyword.value.trim())}`
    const page = await api(url)
    items.value = page.content || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() { load() }
function clearSearch() { keyword.value = ''; load() }

function onCategory(catId) {
  selectedCategory.value = catId
  load()
}

onMounted(load)
</script>

<template>
  <div class="layout">
    <CategoryNav @select="onCategory" />
    <div class="content">
      <div class="searchRow">
        <input
          v-model="keyword" class="searchInput" placeholder="搜索商品..."
          @keyup.enter="search"
        />
        <button class="ghost" @click="search">搜索</button>
        <button v-if="keyword" class="ghost" @click="clearSearch">清除</button>
      </div>
      <div class="head">
        <h2 class="h2">商品{{ selectedCategory ? '' : '' }}</h2>
        <button class="ghost" @click="load">刷新</button>
      </div>

      <p v-if="loading" class="muted">加载中...</p>
      <p v-else-if="error" class="error">{{ error }}</p>

      <div v-else class="grid">
        <div
          v-for="p in items" :key="p.id"
          class="card" @click="router.push(`/products/${p.id}`)"
        >
          <div class="cover" :style="{ backgroundImage: `url(${p.coverImageUrl})` }" />
          <div class="info">
            <div class="title">{{ p.title }}</div>
            <div class="price">¥{{ (p.priceCent / 100).toFixed(2) }}</div>
          </div>
        </div>
      </div>
      <p v-if="!loading && !error && !items.length" class="muted">暂无商品</p>
    </div>
  </div>
</template>

<style scoped>
.layout { display: flex; gap: 24px; }
.content { flex: 1; min-width: 0; }
.searchRow {
  display: flex; gap: 8px; align-items: center; margin-bottom: 12px;
}
.searchInput {
  flex: 1; max-width: 320px;
  border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  padding: 8px 12px; outline: none; font-size: 13px;
}
.head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 14px;
}
.h2 { margin: 0; font-size: 18px; }
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (min-width: 760px) { .grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (min-width: 960px) { .grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
.card {
  border: 1px solid rgba(0,0,0,0.08); border-radius: 16px;
  overflow: hidden; background: white; cursor: pointer;
  transition: transform 0.12s ease;
}
.card:hover { transform: translateY(-2px); }
.cover {
  height: 140px;
  background-color: #f3f3f3; background-position: center; background-size: cover; background-repeat: no-repeat;
}
.info { padding: 10px 10px 12px; display: grid; gap: 6px; }
.title {
  font-size: 13px; color: rgba(0,0,0,0.85); line-height: 1.35;
  height: 2.7em; overflow: hidden;
}
.price { font-weight: 600; font-size: 14px; }
.ghost {
  border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 6px 10px; border-radius: 10px; cursor: pointer;
}
.muted { color: rgba(0,0,0,0.55); }
.error { color: #b00020; }
</style>

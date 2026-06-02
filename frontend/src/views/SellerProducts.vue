<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const sellerId = ref(Number(route.params.id))
const seller = ref(null)
const sellerRating = ref(null)
const products = ref([])
const soldProducts = ref([])
const loading = ref(true)
const error = ref('')
const tab = ref('sale') // 'sale' | 'sold'

async function load() {
  loading.value = true; error.value = ''
  try {
    const [s, page, rating, sold] = await Promise.all([
      api(`/api/users/${sellerId.value}/public`, { auth: false }),
      api(`/api/users/${sellerId.value}/products?page=0&size=50`, { auth: false }),
      api(`/api/users/${sellerId.value}/rating`, { auth: false }),
      api(`/api/users/${sellerId.value}/sold`, { auth: false }),
    ])
    seller.value = s
    sellerRating.value = rating
    products.value = page.content || []
    soldProducts.value = sold || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <button class="back" @click="$router.back()">返回</button>

  <p v-if="loading" class="muted">加载中...</p>
  <p v-else-if="error" class="error">{{ error }}</p>

  <template v-else>
    <div class="sellerHead">
      <div class="avatar" :style="seller.avatarUrl ? { backgroundImage: `url(${seller.avatarUrl})` } : {}">
        <span v-if="!seller.avatarUrl">{{ (seller.nickname || '?')[0] }}</span>
      </div>
      <div class="sellerInfo">
        <h2>{{ seller.nickname }}</h2>
        <div class="ratingRow">
          <span v-if="sellerRating && sellerRating.totalCount > 0" class="starsText">
            {{ '★'.repeat(Math.round(sellerRating.averageScore)) }}{{ '☆'.repeat(5 - Math.round(sellerRating.averageScore)) }}
            {{ sellerRating.averageScore }} 分 ({{ sellerRating.totalCount }} 评)
          </span>
          <span v-else class="muted">暂无评分</span>
        </div>
        <span class="muted">在售 {{ products.length }} 件 · 已售 {{ soldProducts.length }} 件</span>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button :class="['tab', { active: tab === 'sale' }]" @click="tab = 'sale'">在售</button>
      <button :class="['tab', { active: tab === 'sold' }]" @click="tab = 'sold'">已售出</button>
    </div>

    <!-- 在售 -->
    <div v-if="tab === 'sale'">
      <div v-if="!products.length" class="muted" style="margin-top:16px">暂无在售商品</div>
      <div v-else class="grid">
        <div v-for="p in products" :key="p.id" class="card" @click="router.push(`/products/${p.id}`)">
          <div class="cover" :style="{ backgroundImage: `url(${p.coverImageUrl})` }" />
          <div class="info">
            <div class="title">{{ p.title }}</div>
            <div class="price">¥{{ (p.priceCent / 100).toFixed(2) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 已售出 -->
    <div v-if="tab === 'sold'">
      <div v-if="!soldProducts.length" class="muted" style="margin-top:16px">暂无已售出商品</div>
      <div v-else class="soldList">
        <div v-for="s in soldProducts" :key="s.orderId" class="soldItem" @click="router.push(`/products/${s.productId}`)">
          <div class="soldCover" :style="s.productCover ? { backgroundImage: `url(${s.productCover})` } : {}" />
          <div class="soldInfo">
            <span class="soldTitle">{{ s.productTitle }}</span>
            <span class="soldPrice">¥{{ (s.priceCent / 100).toFixed(2) }}</span>
            <span class="soldDate">{{ s.completedAt?.substring(0, 10) }}</span>
          </div>
          <div class="soldRating">
            <template v-if="s.ratingScore">
              <span class="starsSmall">{{ '★'.repeat(s.ratingScore) }}</span>
              <span v-if="s.ratingComment" class="ratingNote">"{{ s.ratingComment }}"</span>
            </template>
            <span v-else class="noRate">暂无评分</span>
          </div>
        </div>
      </div>
    </div>
  </template>
</template>

<style scoped>
.back { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; margin-bottom: 14px; }
.sellerHead { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.avatar {
  width: 60px; height: 60px; border-radius: 50%;
  background-color: #e0e0e0; background-position: center; background-size: cover; background-repeat: no-repeat;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; color: rgba(0,0,0,0.4); flex-shrink: 0;
}
.sellerInfo h2 { margin: 0 0 2px; font-size: 18px; }
.ratingRow { margin-bottom: 2px; }
.starsText { color: #f5a623; font-size: 13px; }

.tabs { display: flex; gap: 0; margin-bottom: 16px; border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; overflow: hidden; width: fit-content; }
.tab { border: 0; background: white; padding: 8px 20px; cursor: pointer; font-size: 13px; color: rgba(0,0,0,0.55); }
.tab:first-child { border-right: 1px solid rgba(0,0,0,0.1); }
.tab.active { background: black; color: white; }

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
.cover { height: 140px; background-color: #f3f3f3; background-position: center; background-size: cover; background-repeat: no-repeat; }
.info { padding: 10px 10px 12px; display: grid; gap: 6px; }
.title { font-size: 13px; color: rgba(0,0,0,0.85); line-height: 1.35; height: 2.7em; overflow: hidden; }
.price { font-weight: 600; font-size: 14px; }

.soldList { display: grid; gap: 8px; }
.soldItem {
  display: flex; gap: 10px; align-items: center;
  padding: 10px; border: 1px solid rgba(0,0,0,0.06); border-radius: 12px;
  cursor: pointer; transition: background 0.12s;
}
.soldItem:hover { background: rgba(0,0,0,0.015); }
.soldCover {
  width: 52px; height: 52px; border-radius: 8px; flex-shrink: 0;
  background-color: #f3f3f3; background-position: center; background-size: cover; background-repeat: no-repeat;
}
.soldInfo { flex: 1; min-width: 0; display: grid; gap: 2px; }
.soldTitle { font-size: 13px; font-weight: 500; }
.soldPrice { font-size: 13px; color: rgba(0,0,0,0.55); }
.soldDate { font-size: 11px; color: rgba(0,0,0,0.35); }
.soldRating { flex-shrink: 0; text-align: right; }
.starsSmall { color: #f5a623; font-size: 14px; }
.ratingNote { font-size: 11px; color: rgba(0,0,0,0.4); display: block; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.noRate { font-size: 11px; color: rgba(0,0,0,0.25); }

.muted { color: rgba(0,0,0,0.55); }
.error { color: #b00020; }
</style>

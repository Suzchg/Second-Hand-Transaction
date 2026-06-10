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
const tab = ref('sale')

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
  <div class="page">
    <button class="back" @click="$router.back()">← 返回</button>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="errText">{{ error }}</p>

    <template v-else>
      <!-- 卖家信息卡片 -->
      <div class="sellerCard">
        <div class="sellerTop">
          <div
            class="avatar"
            :style="seller.avatarUrl ? { backgroundImage: `url(${seller.avatarUrl})` } : {}"
          >
            <span v-if="!seller.avatarUrl" class="avatarFallback">{{ (seller.nickname || '?')[0] }}</span>
          </div>
          <div class="sellerMeta">
            <h2 class="sellerName">{{ seller.nickname }}</h2>
            <div class="ratingRow">
              <template v-if="sellerRating && sellerRating.totalCount > 0">
                <span class="stars">{{ '★'.repeat(Math.round(sellerRating.averageScore)) }}</span>
                <span class="ratingScore">{{ sellerRating.averageScore }}</span>
                <span class="ratingCount">({{ sellerRating.totalCount }} 条评价)</span>
              </template>
              <span v-else class="noRating">暂无评分</span>
            </div>
          </div>
        </div>
        <div class="statsRow">
          <div class="stat">
            <span class="statNum">{{ products.length }}</span>
            <span class="statLabel">在售</span>
          </div>
          <div class="statDivider" />
          <div class="stat">
            <span class="statNum">{{ soldProducts.length }}</span>
            <span class="statLabel">已售</span>
          </div>
        </div>
      </div>

      <!-- 标签切换 -->
      <div class="tabs">
        <button :class="['tab', { active: tab === 'sale' }]" @click="tab = 'sale'">在售商品</button>
        <button :class="['tab', { active: tab === 'sold' }]" @click="tab = 'sold'">已售出</button>
      </div>

      <!-- 在售 -->
      <div v-if="tab === 'sale'">
        <p v-if="!products.length" class="empty">暂无在售商品</p>
        <div v-else class="grid">
          <div
            v-for="p in products" :key="p.id"
            class="productCard" @click="router.push(`/products/${p.id}`)"
          >
            <div class="cover" v-lazy-bg="p.coverImageUrl || ''">
              <span v-if="!p.coverImageUrl" class="coverFallback"><AppIcon name="image" :size="36"/></span>
            </div>
            <div class="cardBody">
              <div class="cardTitle">{{ p.title }}</div>
              <div class="cardPrice">¥{{ (p.priceCent / 100).toFixed(2) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 已售 -->
      <div v-if="tab === 'sold'">
        <p v-if="!soldProducts.length" class="empty">暂无已售出商品</p>
        <div v-else class="grid">
          <div
            v-for="s in soldProducts" :key="s.orderId"
            class="productCard soldCard" @click="router.push(`/products/${s.productId}`)"
          >
            <div class="cover" v-lazy-bg="s.productCover || ''">
              <span v-if="!s.productCover" class="coverFallback"><AppIcon name="image" :size="36"/></span>
            </div>
            <div class="cardBody">
              <div class="cardTitle">{{ s.productTitle }}</div>
              <div class="cardPrice">¥{{ (s.priceCent / 100).toFixed(2) }}</div>
              <div class="cardFooter">
                <span v-if="s.ratingScore" class="soldStars">{{ '★'.repeat(s.ratingScore) }}</span>
                <span v-else class="soldNoRate">暂无评分</span>
                <span class="soldDate">{{ s.completedAt?.substring(0, 10) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page { max-width: 720px; margin: 0 auto; }

.back {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 7px 14px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: var(--space-lg);
  transition: all var(--transition-fast);
}

.back:hover { border-color: var(--border-strong); color: var(--text-primary); }

/* 卖家卡片 */
.sellerCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: var(--space-2xl);
  margin-bottom: var(--space-lg);
}

.sellerTop { display: flex; align-items: center; gap: var(--space-lg); }

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  flex-shrink: 0;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
}

.avatarFallback { font-size: 26px; color: var(--text-tertiary); font-weight: 600; }

.sellerMeta { min-width: 0; }

.sellerName { margin: 0 0 var(--space-xs); font-size: 20px; font-weight: 700; }

.ratingRow { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.stars { color: var(--brand-dark); font-size: 15px; letter-spacing: 1px; }
.ratingScore { font-weight: 700; font-size: 14px; color: var(--text-secondary); }
.ratingCount { font-size: 13px; color: var(--text-tertiary); }
.noRating { font-size: 13px; color: var(--text-tertiary); }

.statsRow {
  display: flex;
  align-items: center;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--border-light);
}

.stat { flex: 1; text-align: center; }
.statNum { display: block; font-size: 22px; font-weight: 700; color: var(--text-primary); }
.statLabel { display: block; font-size: 12px; color: var(--text-tertiary); margin-top: 2px; }
.statDivider { width: 1px; height: 32px; background: var(--border-light); }

/* 标签 */
.tabs {
  display: flex;
  gap: 2px;
  margin-bottom: var(--space-lg);
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 4px;
  width: fit-content;
}

.tab {
  border: 0;
  background: transparent;
  padding: 9px 22px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-tertiary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}

.tab.active {
  background: var(--brand-gradient);
  color: white;
  box-shadow: var(--shadow-brand);
}

.tab:hover:not(.active) { color: var(--text-secondary); }

/* 商品网格 */
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
}

@media (min-width: 640px) { .grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }

.productCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.productCard:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.cover {
  width: 100%;
  aspect-ratio: 1 / 1;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
}

.coverFallback { display: flex; align-items: center; justify-content: center; opacity: 0.3; }

.soldCard .cover { opacity: 0.85; }

.cardBody { padding: var(--space-md); display: grid; gap: var(--space-sm); }

.cardTitle {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cardPrice { font-weight: 700; font-size: 15px; color: var(--accent); }

.cardFooter { display: flex; justify-content: space-between; align-items: center; }

.soldStars { color: var(--brand-dark); font-size: 12px; }
.soldNoRate { font-size: 11px; color: var(--text-tertiary); }
.soldDate { font-size: 11px; color: var(--text-tertiary); }

.empty { text-align: center; padding: 40px 0; color: var(--text-tertiary); font-size: 14px; }
.muted { text-align: center; padding: 40px; color: var(--text-tertiary); }
.errText { color: var(--error); }
</style>

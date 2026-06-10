<script setup>
import { onMounted, ref, watch } from 'vue'
import { api } from '../api.js'
import { useRouter, useRoute } from 'vue-router'
import CategoryNav from '../components/CategoryNav.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(true)
const error = ref('')
const items = ref([])
const latestItems = ref([])
const selectedCategory = ref(0)
const keyword = ref('')

// 成色文本映射
function conditionText(c) {
  const m = {
    NEW: '全新', LIKE_NEW: '99新', NINE_TENTHS: '9成新',
    EIGHT_TENTHS: '8成新', SEVEN_TENTHS: '7成新', SIX_TENTHS_AND_BELOW: '6成新及以下',
  }
  return m[c] || c
}

// 从 URL query 读取搜索关键词
watch(() => route.query.keyword, (kw) => {
  if (kw) {
    keyword.value = kw
    load()
  }
}, { immediate: true })

async function load() {
  loading.value = true
  error.value = ''
  try {
    let url = '/api/products?page=0&size=24'
    if (selectedCategory.value != null && selectedCategory.value !== 0) url += `&categoryId=${selectedCategory.value}`
    if (keyword.value.trim()) url += `&keyword=${encodeURIComponent(keyword.value.trim())}`
    const page = await api(url)
    items.value = page.content || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadLatest() {
  try {
    const page = await api('/api/products?page=0&size=8')
    latestItems.value = page.content || []
  } catch { /* ignore */ }
}

function search() {
  if (keyword.value.trim()) {
    router.push({ path: '/', query: { keyword: keyword.value.trim() } })
  }
  load()
  loadLatest()
}

function clearSearch() {
  keyword.value = ''
  router.push({ path: '/' })
  load()
  loadLatest()
}

function onCategory(catId) {
  selectedCategory.value = catId
  load()
}

watch(selectedCategory, () => { if (selectedCategory.value === 0) loadLatest() })

onMounted(() => {
  // 读取 URL query
  const q = route.query.keyword
  if (q) keyword.value = q
  load()
  loadLatest()
})
</script>

<template>
  <div class="homeLayout">
    <CategoryNav @select="onCategory" />

    <div class="mainContent">
      <!-- 顶部横幅（推荐页） -->
      <div v-if="selectedCategory === 0 && !keyword" class="banner">
        <div class="bannerContent">
          <h1 class="bannerTitle">Second hand</h1>
          <p class="bannerSub">Buy smart, sell simple — every pre-loved item deserves a second life</p>
        </div>
        <div class="bannerDecor"><AppIcon name="shopping-bag" :size="48"/></div>
      </div>

      <!-- 分类标题 + 操作 -->
      <div class="sectionHead">
        <h2 class="sectionTitle">
          <AppIcon :name="selectedCategory === 0 ? 'sparkle' : 'package'" :size="18"/>
          {{ selectedCategory === 0 ? '为你推荐' : '商品列表' }}
          <span v-if="keyword" class="searchHint">— 搜索 "{{ keyword }}"</span>
        </h2>
        <div class="sectionActions">
          <button v-if="keyword" class="actionLink" @click="clearSearch">清除搜索</button>
          <button class="actionLink" @click="load">刷新</button>
        </div>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="skeletonGrid">
        <div v-for="n in 8" :key="n" class="skeletonCard">
          <div class="skeletonImg" />
          <div class="skeletonInfo">
            <div class="skeletonLine w80" />
            <div class="skeletonLine w40" />
          </div>
        </div>
      </div>

      <!-- 错误 -->
      <div v-else-if="error" class="errorState">
        <span class="errorIcon"><AppIcon name="alert-triangle" :size="48"/></span>
        <p>{{ error }}</p>
        <button class="retryBtn" @click="load">重试</button>
      </div>

      <!-- 商品网格 -->
      <div v-else class="productGrid">
        <div
          v-for="p in items" :key="p.id"
          class="productCard"
          @click="router.push(`/products/${p.id}`)"
        >
          <div class="cardImage" v-lazy-bg="p.coverImageUrl || ''">
            <div v-if="!p.coverImageUrl" class="noImage"><AppIcon name="image" :size="36"/></div>
            <span v-if="p.condition" class="condBadge">{{ conditionText(p.condition) }}</span>
          </div>
          <div class="cardBody">
            <div class="cardTitle">{{ p.title }}</div>
            <div class="cardFooter">
              <span class="cardPrice">¥{{ (p.priceCent / 100).toFixed(2) }}</span>
              <span v-if="p.freeShipping" class="freeShip">包邮</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && !error && !items.length" class="emptyState">
        <span class="emptyIcon"><AppIcon name="mail" :size="48"/></span>
        <p>暂无商品</p>
      </div>

      <!-- 新上架（仅在推荐页无搜索时） -->
      <template v-if="selectedCategory === 0 && !keyword && latestItems.length">
        <div class="sectionHead" style="margin-top: var(--space-3xl)">
          <h2 class="sectionTitle"><AppIcon name="rocket" :size="18"/> 刚刚上架</h2>
        </div>
        <div class="productGrid">
          <div
            v-for="p in latestItems" :key="'latest-' + p.id"
            class="productCard"
            @click="router.push(`/products/${p.id}`)"
          >
            <div class="cardImage" v-lazy-bg="p.coverImageUrl || ''">
              <div v-if="!p.coverImageUrl" class="noImage"><AppIcon name="image" :size="36"/></div>
            </div>
            <div class="cardBody">
              <div class="cardTitle">{{ p.title }}</div>
              <div class="cardFooter">
                <span class="cardPrice">¥{{ (p.priceCent / 100).toFixed(2) }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>


<style scoped>
.homeLayout {
  display: flex;
  gap: var(--space-2xl);
}

.mainContent {
  flex: 1;
  min-width: 0;
}

/* Banner */
.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--brand-gradient-soft);
  border-radius: var(--radius-xl);
  padding: var(--space-2xl) var(--space-3xl);
  margin-bottom: var(--space-2xl);
  border: 1px solid rgba(14, 181, 166, 0.2);
}

.bannerContent {
  display: grid;
  gap: var(--space-sm);
}

.bannerTitle {
  font-size: 28px;
  font-weight: 800;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
  letter-spacing: 2px;
  font-style: italic;
}

.bannerSub {
  font-size: var(--text-base);
  color: var(--text-secondary);
}

.bannerDecor {
  opacity: 0.6;
  color: var(--brand);
}

/* 分区头部 */
.sectionHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.sectionTitle {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.searchHint {
  font-weight: 400;
  color: var(--text-tertiary);
  font-size: var(--text-base);
}

.sectionActions {
  display: flex;
  gap: var(--space-md);
}

.actionLink {
  background: none;
  border: none;
  color: var(--text-tertiary);
  font-size: var(--text-sm);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.actionLink:hover {
  color: var(--brand-darker);
  background: var(--brand-light);
}

/* 商品网格 */
.productGrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
}

@media (min-width: 760px) {
  .productGrid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (min-width: 1020px) {
  .productGrid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

/* 商品卡片 */
.productCard {
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-normal);
  border: 1px solid var(--border-light);
  animation: fadeInUp 0.4s ease both;
}

.productCard:nth-child(1) { animation-delay: 0s; }
.productCard:nth-child(2) { animation-delay: 0.05s; }
.productCard:nth-child(3) { animation-delay: 0.1s; }
.productCard:nth-child(4) { animation-delay: 0.15s; }
.productCard:nth-child(5) { animation-delay: 0.05s; }
.productCard:nth-child(6) { animation-delay: 0.1s; }
.productCard:nth-child(7) { animation-delay: 0.15s; }
.productCard:nth-child(8) { animation-delay: 0.2s; }

.productCard:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
  border-color: transparent;
}

.cardImage {
  height: 180px;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.noImage {
  opacity: 0.3;
  display: flex;
  align-items: center;
  justify-content: center;
}

.condBadge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: white;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: var(--radius-xs);
  backdrop-filter: blur(4px);
}

.cardBody {
  padding: var(--space-md) var(--space-md) var(--space-lg);
  display: grid;
  gap: var(--space-sm);
}

.cardTitle {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.cardFooter {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.cardPrice {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
}

.freeShip {
  font-size: 10px;
  color: var(--text-inverse);
  background: var(--brand-dark);
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  font-weight: 500;
}

/* 骨架屏 */
.skeletonGrid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-lg);
}

@media (max-width: 1019px) { .skeletonGrid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 759px) { .skeletonGrid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

.skeletonCard {
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--border-light);
}

.skeletonImg {
  height: 180px;
  background: linear-gradient(90deg, var(--bg-secondary) 25%, #eee 50%, var(--bg-secondary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeletonInfo {
  padding: var(--space-md);
  display: grid;
  gap: var(--space-sm);
}

.skeletonLine {
  height: 14px;
  background: linear-gradient(90deg, var(--bg-secondary) 25%, #eee 50%, var(--bg-secondary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-xs);
}

.skeletonLine.w80 { width: 80%; }
.skeletonLine.w40 { width: 40%; }

/* 空状态 & 错误 */
.emptyState, .errorState {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-tertiary);
}

.emptyIcon, .errorIcon {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-md);
  color: var(--text-tertiary);
}

.retryBtn {
  margin-top: var(--space-md);
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 8px 20px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}

.retryBtn:hover {
  border-color: var(--brand-dark);
  color: var(--brand-darker);
}

/* 响应式 */
@media (max-width: 640px) {
  .homeLayout { flex-direction: column; gap: var(--space-md); }

  .banner {
    padding: var(--space-xl);
    border-radius: var(--radius-lg);
  }

  .bannerTitle { font-size: 20px; letter-spacing: 1px; }
  .bannerDecor { font-size: 36px; }

  .cardImage { height: 140px; }

  .productGrid { gap: var(--space-sm); }

  .sectionTitle { font-size: 16px; }
}
</style>

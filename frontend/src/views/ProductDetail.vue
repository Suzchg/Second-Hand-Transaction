<script setup>
import { onMounted, ref, computed } from 'vue'
import { api } from '../api.js'
import { useRoute, useRouter } from 'vue-router'
import AddressPicker from '../components/AddressPicker.vue'
import ImageGallery from '../components/ImageGallery.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref('')
const p = ref(null)
const seller = ref(null)
const pid = ref(Number(route.params.id))
const showAddressPicker = ref(false)

// ---- 砍价 ----
const showOfferModal = ref(false)
const offerPriceYuan = ref('')
const offerMessage = ref('')
const offerError = ref('')
const offerSuccess = ref('')
const offerSubmitting = ref(false)
const currentUserId = computed(() => Number(localStorage.getItem('userId') || '0'))
const isLoggedIn = computed(() => !!localStorage.getItem('token'))
const isOwner = computed(() => p.value?.sellerId === currentUserId.value)

// ---- 收藏 ----
const isFavorited = ref(false)
const favoriteToggling = ref(false)

async function checkFavorite() {
  if (!isLoggedIn.value) return
  try { isFavorited.value = !!(await api(`/api/products/${pid.value}/favorite/status`)) }
  catch { /* ignore */ }
}

async function toggleFavorite() {
  if (!isLoggedIn.value) { router.push('/login'); return }
  favoriteToggling.value = true
  try {
    if (isFavorited.value) {
      await api(`/api/products/${pid.value}/favorite`, { method: 'DELETE' })
      isFavorited.value = false
    } else {
      await api(`/api/products/${pid.value}/favorite`, { method: 'POST' })
      isFavorited.value = true
    }
  } catch (e) { alert(e.message) }
  finally { favoriteToggling.value = false }
}

const conditionLabels = {
  NEW: '全新', LIKE_NEW: '99新', NINE_TENTHS: '9成新',
  EIGHT_TENTHS: '8成新', SEVEN_TENTHS: '7成新', SIX_TENTHS_AND_BELOW: '6成新及以下',
}
function conditionLabel(c) { return conditionLabels[c] || c }

async function load() {
  loading.value = true; error.value = ''
  try {
    p.value = await api(`/api/products/${route.params.id}`)
    if (p.value?.sellerId) {
      try { seller.value = await api(`/api/users/${p.value.sellerId}/public`, { auth: false }) }
      catch { /* ignore */ }
    }
  }
  catch (e) { error.value = e.message || '加载失败' }
  finally { loading.value = false }
}

// ---- 评论区 ----
const comments = ref([])
const commentText = ref('')
const commentError = ref('')
const commentSubmitting = ref(false)
const commentNicknames = ref({})

async function loadComments() {
  try {
    const list = await api(`/api/products/${pid.value}/comments`, { auth: false })
    comments.value = list || []
    const ids = [...new Set(comments.value.map(c => c.userId).filter(Boolean))]
    await Promise.all(ids.map(async id => {
      try {
        const u = await api(`/api/users/${id}/public`, { auth: false })
        commentNicknames.value[id] = u.nickname
      } catch { commentNicknames.value[id] = `用户 #${id}` }
    }))
  } catch { /* ignore */ }
}

async function submitComment() {
  commentError.value = ''
  if (!commentText.value.trim()) { commentError.value = '请输入评论'; return }
  commentSubmitting.value = true
  try {
    await api(`/api/products/${pid.value}/comments`, {
      method: 'POST',
      body: { content: commentText.value.trim() },
    })
    commentText.value = ''
    await loadComments()
  } catch (e) { commentError.value = e.message }
  finally { commentSubmitting.value = false }
}

// ---- 举报 ----
const showReportModal = ref(false)
const reportReason = ref(null)
const reportDescription = ref('')
const reportError = ref('')
const reportSuccess = ref('')
const reportSubmitting = ref(false)

const reportReasons = [
  { value: 'COUNTERFEIT', label: '假冒伪劣' },
  { value: 'PROHIBITED', label: '违禁物品' },
  { value: 'FALSE_DESC', label: '虚假描述' },
  { value: 'PRICE_FRAUD', label: '价格欺诈' },
  { value: 'PRIVACY', label: '侵犯隐私' },
  { value: 'OTHER', label: '其他' },
]

function openReport() {
  reportReason.value = null
  reportDescription.value = ''
  reportError.value = ''
  reportSuccess.value = ''
  showReportModal.value = true
}

async function submitReport() {
  reportError.value = ''
  reportSuccess.value = ''
  if (!reportReason.value) { reportError.value = '请选择举报原因'; return }
  reportSubmitting.value = true
  try {
    await api(`/api/products/${pid.value}/report`, {
      method: 'POST',
      body: {
        reasonType: reportReason.value,
        description: reportDescription.value.trim() || null,
      },
    })
    reportSuccess.value = '举报已提交，我们将尽快处理'
    setTimeout(() => { showReportModal.value = false }, 1800)
  } catch (e) { reportError.value = e.message }
  finally { reportSubmitting.value = false }
}

function buy() { showAddressPicker.value = true }

async function onAddress(addr) {
  showAddressPicker.value = false
  try {
    const order = await api('/api/orders', {
      method: 'POST',
      body: {
        productId: Number(route.params.id),
        receiverName: addr.receiverName,
        receiverPhone: addr.receiverPhone,
        receiverAddress: addr.receiverAddress,
      },
    })
    router.push(`/orders/${order.id}`)
  } catch (e) {
    alert(e.message || '下单失败')
  }
}

function openOffer() {
  offerPriceYuan.value = ''
  offerMessage.value = ''
  offerError.value = ''
  offerSuccess.value = ''
  showOfferModal.value = true
}

async function submitOffer() {
  offerError.value = ''
  offerSuccess.value = ''
  const priceCent = Math.round(Number(offerPriceYuan.value || '0') * 100)
  if (!priceCent || priceCent <= 0) { offerError.value = '请输入有效报价金额'; return }
  if (priceCent >= p.value.priceCent) { offerError.value = '报价应低于卖家标价'; return }
  offerSubmitting.value = true
  try {
    await api(`/api/products/${pid.value}/offers`, {
      method: 'POST',
      body: { offeredPriceCent: priceCent, message: offerMessage.value.trim() || null },
    })
    offerSuccess.value = '报价已提交，等待卖家回复'
    setTimeout(() => { showOfferModal.value = false }, 1500)
  } catch (e) {
    offerError.value = e.message || '提交失败'
  } finally {
    offerSubmitting.value = false
  }
}

onMounted(() => { load(); loadComments(); checkFavorite() })
</script>

<template>
  <div class="page">
    <!-- 返回按钮 -->
    <button class="backBtn" @click="$router.back()">← 返回</button>

    <!-- 加载与错误 -->
    <div v-if="loading" class="loadingState">
      <div class="skeletonDetail">
        <div class="skImage" />
        <div class="skInfo">
          <div class="skLine w60" />
          <div class="skLine w30" />
          <div class="skLine w80" />
        </div>
      </div>
    </div>
    <div v-else-if="error" class="errorState">
      <span class="errorIcon"><AppIcon name="alert-triangle" :size="48"/></span>
      <p>{{ error }}</p>
    </div>

    <!-- 主体内容 -->
    <div v-else class="detailLayout">
      <!-- 左：图片 -->
      <div class="detailLeft">
        <ImageGallery :productId="pid" />
      </div>

      <!-- 右：信息 -->
      <div class="detailRight">
        <!-- 标题 -->
        <h1 class="productTitle">{{ p.title }}</h1>

        <!-- 标签 -->
        <div class="tagRow">
          <span v-if="p.condition" class="condTag">{{ conditionLabel(p.condition) }}</span>
          <span v-if="p.freeShipping" class="shipTag free"><AppIcon name="truck" :size="14"/> 包邮</span>
          <span v-else-if="p.shippingFeeCent > 0" class="shipTag paid">运费 ¥{{ (p.shippingFeeCent / 100).toFixed(2) }}</span>
        </div>

        <!-- 价格 -->
        <div class="priceRow">
          <span class="priceSymbol">¥</span>
          <span class="priceValue">{{ (p.priceCent / 100).toFixed(2) }}</span>
        </div>

        <!-- 卖家信息卡片 -->
        <div v-if="seller" class="sellerCard" @click="router.push(`/seller/${seller.userId}`)">
          <div class="sellerAvatar" :style="seller.avatarUrl ? { backgroundImage: `url(${seller.avatarUrl})` } : {}">
            <span v-if="!seller.avatarUrl">{{ (seller.nickname || '?')[0] }}</span>
          </div>
          <div class="sellerInfo">
            <div class="sellerName">{{ seller.nickname }}</div>
            <div class="sellerStats">
              <span v-if="seller.ratingCount > 0" class="sellerStars">
                {{ '★'.repeat(Math.round(seller.averageRating)) }} {{ seller.averageRating }}
              </span>
              <span class="sellerCount">({{ seller.ratingCount || 0 }} 评价)</span>
            </div>
          </div>
          <span class="sellerArrow">查看主页 ›</span>
        </div>

        <!-- 描述 -->
        <div class="descCard">
          <h3 class="descTitle"><AppIcon name="file-text" :size="16"/> 商品描述</h3>
          <p class="descContent">{{ p.description }}</p>
        </div>

        <!-- 操作按钮区 -->
        <div v-if="isLoggedIn && !isOwner && p.status === 'ON_SALE'" class="actionBar">
          <button class="btnPrimary" @click="buy">立即购买</button>
          <button class="btnOffer" @click="openOffer"><AppIcon name="dollar" :size="16"/> 砍价</button>
          <button class="btnFav" :class="{ favorited: isFavorited }" :disabled="favoriteToggling" @click="toggleFavorite">
            <AppIcon name="heart" :size="16" :active="isFavorited"/>
            {{ isFavorited ? '已收藏' : '收藏' }}
          </button>
          <button class="btnChat" @click="router.push('/messages?product=' + pid)"><AppIcon name="message-square" :size="16"/> 私聊</button>
          <button class="btnReport" @click="openReport">举报</button>
        </div>
      </div>
    </div>

    <!-- 评论区 -->
    <div v-if="!loading && !error" class="commentsCard">
      <h3 class="commentsTitle"><AppIcon name="chat" :size="16"/> 评论 ({{ comments.length }})</h3>

      <!-- 评论输入 -->
      <div v-if="isLoggedIn" class="commentForm">
        <textarea v-model="commentText" rows="2" placeholder="写下你的评论..." />
        <button class="submitCommentBtn" :disabled="commentSubmitting" @click="submitComment">
          {{ commentSubmitting ? '发表中...' : '发表评论' }}
        </button>
        <p v-if="commentError" class="fieldError">{{ commentError }}</p>
      </div>
      <p v-else class="loginHint">请<a href="/login">登录</a>后发表评论</p>

      <!-- 评论列表 -->
      <div v-if="!comments.length" class="noComments">暂无评论，来发表第一条吧</div>
      <div v-else class="commentList">
        <div v-for="c in comments" :key="c.id" class="commentItem">
          <div class="commentHead">
            <div class="commentAvatar">{{ (commentNicknames[c.userId] || '?')[0] }}</div>
            <span class="commentUser">{{ commentNicknames[c.userId] || `用户 #${c.userId}` }}</span>
            <span class="commentTime">{{ c.createdAt?.substring(0, 16) }}</span>
          </div>
          <p class="commentContent">{{ c.content }}</p>
        </div>
      </div>
    </div>

    <!-- 砍价弹窗 -->
    <Teleport to="body">
      <div v-if="showOfferModal" class="modalOverlay" @click.self="showOfferModal = false">
        <div class="modalCard">
          <h3><AppIcon name="dollar" :size="18"/> 发起报价</h3>
          <div class="modalPrice">卖家标价：<strong>¥{{ (p.priceCent / 100).toFixed(2) }}</strong></div>
          <label>您的报价（元）
            <input v-model="offerPriceYuan" type="number" step="0.01" min="0" placeholder="例如：1500.00" />
          </label>
          <label>留言（选填）
            <input v-model="offerMessage" placeholder="例如：可以便宜点吗？" />
          </label>
          <p v-if="offerError" class="fieldError">{{ offerError }}</p>
          <p v-if="offerSuccess" class="fieldSuccess">{{ offerSuccess }}</p>
          <div class="modalActions">
            <button class="btnPrimary" :disabled="offerSubmitting" @click="submitOffer">
              {{ offerSubmitting ? '提交中...' : '提交报价' }}
            </button>
            <button class="btnGhost" @click="showOfferModal = false">取消</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 举报弹窗 -->
    <Teleport to="body">
      <div v-if="showReportModal" class="modalOverlay" @click.self="showReportModal = false">
        <div class="modalCard">
          <h3><AppIcon name="alert-octagon" :size="18"/> 举报商品</h3>
          <div class="reportGrid">
            <label v-for="r in reportReasons" :key="r.value" :class="['reportOption', { selected: reportReason === r.value }]">
              <input type="radio" v-model="reportReason" :value="r.value" />
              {{ r.label }}
            </label>
          </div>
          <label>补充说明（选填）
            <textarea v-model="reportDescription" rows="2" placeholder="请简要描述举报原因..." />
          </label>
          <p v-if="reportError" class="fieldError">{{ reportError }}</p>
          <p v-if="reportSuccess" class="fieldSuccess">{{ reportSuccess }}</p>
          <div class="modalActions">
            <button class="btnPrimary" :disabled="reportSubmitting" @click="submitReport">
              {{ reportSubmitting ? '提交中...' : '提交举报' }}
            </button>
            <button class="btnGhost" @click="showReportModal = false">取消</button>
          </div>
        </div>
      </div>
    </Teleport>

    <AddressPicker v-if="showAddressPicker" @select="onAddress" @close="showAddressPicker = false" />
  </div>
</template>

<style scoped>
.page { max-width: 1000px; margin: 0 auto; }

/* 返回按钮 */
.backBtn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 8px 16px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: var(--space-lg);
  transition: all var(--transition-fast);
}

.backBtn:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

/* 骨架屏 */
.skeletonDetail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2xl);
}

.skImage {
  height: 400px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-lg);
}

.skInfo { display: grid; gap: var(--space-md); align-content: start; }

.skLine {
  height: 20px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-sm);
}

.skLine.w60 { width: 60%; }
.skLine.w30 { width: 30%; }
.skLine.w80 { width: 80%; }

.loadingState { padding: 40px 0; }
.errorState { text-align: center; padding: 60px 20px; color: var(--text-tertiary); }
.errorIcon { font-size: 48px; display: block; margin-bottom: var(--space-md); }

/* 详情布局 */
.detailLayout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3xl);
  margin-bottom: var(--space-3xl);
}

.detailLeft { min-width: 0; }
.detailRight { display: grid; gap: var(--space-lg); align-content: start; }

/* 标题 */
.productTitle {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.4;
  margin: 0;
}

/* 标签行 */
.tagRow {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.condTag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
  background: var(--brand-light);
  color: var(--brand-darker);
}

.shipTag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
}

.shipTag.free { background: var(--success-bg); color: var(--success); }
.shipTag.paid { background: var(--warning-bg); color: var(--warning); }

/* 价格 */
.priceRow {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.priceSymbol {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
}

.priceValue {
  font-size: 32px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1;
}

/* 卖家卡片 */
.sellerCard {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.sellerCard:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-sm);
}

.sellerAvatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.sellerInfo { flex: 1; }

.sellerName {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.sellerStats {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-top: 2px;
}

.sellerStars { color: var(--brand-dark); font-size: 13px; }
.sellerCount { color: var(--text-tertiary); font-size: 12px; }

.sellerArrow {
  color: var(--text-tertiary);
  font-size: 13px;
}

/* 描述 */
.descCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.descTitle {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 var(--space-sm);
}

.descContent {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

/* 操作按钮 */
.actionBar {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.btnPrimary {
  flex: 1;
  min-width: 120px;
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 14px 24px;
  border-radius: var(--radius-md);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
  transition: all var(--transition-fast);
}

.btnPrimary:hover {
  opacity: 0.9;
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(14, 181, 166, 0.4);
}

.btnPrimary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }

.btnOffer {
  border: 1.5px solid var(--brand-dark);
  background: white;
  color: var(--brand-darker);
  padding: 14px 18px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btnOffer:hover {
  background: var(--brand-light);
  border-color: var(--accent);
  color: var(--accent);
}

.btnFav, .btnChat, .btnReport {
  border: 1.5px solid var(--border-default);
  background: var(--bg-primary);
  padding: 14px 18px;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btnFav:hover { border-color: #e91e63; color: #e91e63; }
.btnFav.favorited { border-color: #e91e63; color: #e91e63; background: #fce4ec; }
.btnFav:disabled { opacity: 0.5; cursor: not-allowed; }

.btnChat:hover { border-color: var(--info); color: var(--info); }
.btnReport { color: var(--text-tertiary); }
.btnReport:hover { border-color: var(--error); color: var(--error); }

/* 评论区 */
.commentsCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-xl);
  margin-top: var(--space-3xl);
}

.commentsTitle {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 var(--space-lg);
}

.commentForm {
  display: grid;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}

.commentForm textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  min-height: 56px;
}

.submitCommentBtn {
  justify-self: end;
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 8px 20px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
}

.submitCommentBtn:disabled { opacity: 0.5; cursor: not-allowed; }

.loginHint {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-bottom: var(--space-lg);
}

.noComments {
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
  padding: var(--space-xl);
}

.commentList {
  display: grid;
  gap: var(--space-sm);
}

.commentItem {
  padding: var(--space-md);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
}

.commentItem:hover { background: var(--bg-tertiary); }

.commentHead {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.commentAvatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--brand-gradient-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--brand-darker);
  flex-shrink: 0;
}

.commentUser { font-size: 13px; font-weight: 500; color: var(--text-secondary); }
.commentTime { font-size: 11px; color: var(--text-tertiary); margin-left: auto; }

.commentContent {
  margin: var(--space-sm) 0 0 36px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
}

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
  max-width: 420px;
  display: grid;
  gap: var(--space-md);
  box-shadow: var(--shadow-xl);
  animation: scaleIn 0.25s ease;
}

.modalCard h3 { margin: 0; font-size: 18px; }

.modalPrice { font-size: 14px; color: var(--text-secondary); }

.modalCard label {
  display: grid;
  gap: var(--space-xs);
  font-size: 13px;
  color: var(--text-secondary);
}

.modalCard input, .modalCard textarea {
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
}

.modalCard textarea { resize: vertical; min-height: 60px; }

.modalActions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.btnGhost {
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 10px 20px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
}

.btnGhost:hover { border-color: var(--border-strong); }

.reportGrid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-sm);
}

.reportOption {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 14px;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  transition: all var(--transition-fast);
}

.reportOption:hover { border-color: var(--border-strong); }

.reportOption.selected {
  border-color: var(--brand-dark);
  background: var(--brand-light);
}

.reportOption input[type="radio"] { accent-color: var(--brand-dark); }

.fieldError { color: var(--error); font-size: 13px; margin: 0; }
.fieldSuccess { color: var(--success); font-size: 13px; margin: 0; }

/* 响应式 */
@media (max-width: 768px) {
  .detailLayout {
    grid-template-columns: 1fr;
    gap: var(--space-lg);
  }

  .productTitle { font-size: 18px; }
  .priceValue { font-size: 26px; }

  .actionBar {
    position: sticky;
    bottom: 0;
    background: var(--bg-primary);
    padding: var(--space-md);
    margin: 0 calc(-1 * var(--space-md));
    border-top: 1px solid var(--border-light);
    z-index: 10;
  }

  .btnPrimary { font-size: 15px; padding: 12px 20px; }
  .btnOffer, .btnFav, .btnChat, .btnReport { padding: 12px 14px; font-size: 13px; }

  .reportGrid { grid-template-columns: 1fr; }
}
</style>

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

// ---- 砍价相关 ----
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
    // 获取卖家公开信息
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
    // 批量获取评论者昵称
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
  <button class="back" @click="$router.back()">返回</button>
  <p v-if="loading" class="muted">加载中...</p>
  <p v-else-if="error" class="error">{{ error }}</p>

  <div v-else class="wrap">
    <ImageGallery :productId="pid" />
    <div class="meta">
      <div class="title">{{ p.title }}</div>
      <span v-if="p.condition" class="condTag">{{ conditionLabel(p.condition) }}</span>
      <div class="price">¥{{ (p.priceCent / 100).toFixed(2) }}</div>
    </div>
    <div class="desc">
      <div class="h3">描述</div>
      <div class="text">{{ p.description }}</div>
    </div>

    <!-- 卖家信息 -->
    <div v-if="seller" class="sellerRow" @click="router.push(`/seller/${seller.userId}`)">
      <div class="sellerAvatar" :style="seller.avatarUrl ? { backgroundImage: `url(${seller.avatarUrl})` } : {}">
        <span v-if="!seller.avatarUrl">{{ (seller.nickname || '?')[0] }}</span>
      </div>
      <div class="sellerMeta">
        <span class="sellerName">{{ seller.nickname }}</span>
        <span class="sellerStars" v-if="seller.ratingCount > 0">
          {{ '★'.repeat(Math.round(seller.averageRating)) }} {{ seller.averageRating }} ({{ seller.ratingCount }}评)
        </span>
        <span class="sellerHint">查看在售商品 →</span>
      </div>
    </div>

    <!-- 评论区 -->
    <div class="commentsSection">
      <div class="h3">评论 ({{ comments.length }})</div>
      <!-- 评论输入 -->
      <div v-if="isLoggedIn" class="commentInput">
        <textarea v-model="commentText" rows="2" placeholder="写下你的评论..." />
        <button class="primary small" :disabled="commentSubmitting" @click="submitComment">
          {{ commentSubmitting ? '发表中...' : '发表' }}
        </button>
        <p v-if="commentError" class="error">{{ commentError }}</p>
      </div>
      <p v-else class="muted" style="font-size:13px">请<a href="/login">登录</a>后发表评论</p>
      <!-- 评论列表 -->
      <div v-if="!comments.length" class="muted" style="margin-top:8px">暂无评论</div>
      <div v-else class="commentList">
        <div v-for="c in comments" :key="c.id" class="commentItem">
          <div class="commentHead">
            <span class="commentUser">{{ commentNicknames[c.userId] || `用户 #${c.userId}` }}</span>
            <span class="commentTime">{{ c.createdAt?.substring(0, 16) }}</span>
          </div>
          <p class="commentContent">{{ c.content }}</p>
        </div>
      </div>
    </div>

    <div v-if="isLoggedIn && !isOwner && p.status === 'ON_SALE'" class="actions">
      <button class="primary" @click="buy">立即购买</button>
      <button class="offerBtn" @click="openOffer">砍价</button>
      <button class="favBtn" :class="{ active: isFavorited }" :disabled="favoriteToggling" @click="toggleFavorite">
        {{ isFavorited ? '♥ 已收藏' : '♡ 收藏' }}
      </button>
      <button class="reportBtn" @click="openReport">举报</button>
      <button class="chatBtn" @click="router.push('/messages?product=' + pid)">💬 私聊</button>
    </div>
  </div>

  <!-- 砍价弹窗 -->
  <div v-if="showOfferModal" class="overlay" @click.self="showOfferModal = false">
    <div class="modal">
      <h3>发起报价</h3>
      <div class="info">卖家标价：<strong>¥{{ (p.priceCent / 100).toFixed(2) }}</strong></div>
      <label>您的报价（元）<input v-model="offerPriceYuan" type="number" step="0.01" min="0" placeholder="例如：1500.00" /></label>
      <label>留言（选填）<input v-model="offerMessage" placeholder="例如：可以便宜点吗？" /></label>
      <p v-if="offerError" class="error">{{ offerError }}</p>
      <p v-if="offerSuccess" class="okMsg">{{ offerSuccess }}</p>
      <div class="modalActions">
        <button class="primary" :disabled="offerSubmitting" @click="submitOffer">
          {{ offerSubmitting ? '提交中...' : '提交报价' }}
        </button>
        <button class="ghost" @click="showOfferModal = false">取消</button>
      </div>
    </div>
  </div>

  <!-- 举报弹窗 -->
  <div v-if="showReportModal" class="overlay" @click.self="showReportModal = false">
    <div class="modal">
      <h3>举报商品</h3>
      <div class="reportReasons">
        <label v-for="r in reportReasons" :key="r.value" class="reasonLabel" :class="{ sel: reportReason === r.value }">
          <input type="radio" v-model="reportReason" :value="r.value" />
          {{ r.label }}
        </label>
      </div>
      <label>补充说明（选填）
        <textarea v-model="reportDescription" rows="2" placeholder="请简要描述举报原因..." />
      </label>
      <p v-if="reportError" class="error">{{ reportError }}</p>
      <p v-if="reportSuccess" class="okMsg">{{ reportSuccess }}</p>
      <div class="modalActions">
        <button class="primary" :disabled="reportSubmitting" @click="submitReport">
          {{ reportSubmitting ? '提交中...' : '提交举报' }}
        </button>
        <button class="ghost" @click="showReportModal = false">取消</button>
      </div>
    </div>
  </div>

  <AddressPicker v-if="showAddressPicker" @select="onAddress" @close="showAddressPicker = false" />
</template>

<style scoped>
.back { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; }
.wrap { margin-top: 12px; display: grid; gap: 14px; max-width: 600px; }
.hero { height: 300px; border-radius: 18px; background: #f3f3f3 center / cover no-repeat; border: 1px solid rgba(0,0,0,0.08); }
.meta { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 600; flex: 1; min-width: 0; }
.condTag {
  font-size: 11px; padding: 2px 8px; border-radius: 6px;
  background: #e8f5e9; color: #2e7d32; white-space: nowrap;
  flex-shrink: 0;
}
.price { font-size: 18px; font-weight: 700; flex-shrink: 0; }
.desc { border: 1px solid rgba(0,0,0,0.08); border-radius: 18px; padding: 14px; background: white; }
.h3 { font-weight: 600; margin-bottom: 8px; }
.text { white-space: pre-wrap; line-height: 1.55; color: rgba(0,0,0,0.78); font-size: 14px; }
.sellerRow {
  display: flex; align-items: center; gap: 10px; cursor: pointer;
  padding: 10px 14px; border: 1px solid rgba(0,0,0,0.08); border-radius: 14px;
  transition: background 0.12s;
}
.sellerRow:hover { background: rgba(0,0,0,0.02); }
.sellerAvatar {
  width: 40px; height: 40px; border-radius: 50%;
  background-color: #e0e0e0; background-position: center; background-size: cover; background-repeat: no-repeat;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; color: rgba(0,0,0,0.4); flex-shrink: 0;
}
.sellerMeta { display: flex; flex-direction: column; gap: 2px; }
.sellerName { font-size: 14px; font-weight: 500; }
.sellerStars { font-size: 12px; color: #f5a623; }
.sellerHint { font-size: 12px; color: rgba(0,0,0,0.4); }
/* 评论区 */
.commentsSection {
  border: 1px solid rgba(0,0,0,0.08); border-radius: 18px; padding: 14px; background: white;
}
.commentsSection .h3 { font-weight: 600; margin-bottom: 10px; }
.commentInput { display: grid; gap: 8px; margin-bottom: 12px; }
.commentInput textarea {
  width: 100%; box-sizing: border-box;
  border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  padding: 8px 10px; outline: none; font-family: inherit; font-size: 13px;
  resize: vertical; min-height: 48px;
}
.primary.small { padding: 6px 12px; font-size: 13px; width: fit-content; }
.commentList { display: grid; gap: 8px; }
.commentItem {
  padding: 8px 10px; border: 1px solid rgba(0,0,0,0.06); border-radius: 10px;
}
.commentHead { display: flex; justify-content: space-between; align-items: center; }
.commentUser { font-size: 12px; font-weight: 500; color: rgba(0,0,0,0.6); }
.commentTime { font-size: 11px; color: rgba(0,0,0,0.35); }
.commentContent { margin: 4px 0 0; font-size: 13px; line-height: 1.5; white-space: pre-wrap; }
.primary {
  border: 0; background: black; color: white;
  padding: 12px 14px; border-radius: 14px; cursor: pointer;
}
.primary:disabled { opacity: 0.5; cursor: not-allowed; }
.actions { display: flex; gap: 10px; align-items: center; }
.offerBtn {
  border: 1px solid #e65100; background: white; color: #e65100;
  padding: 12px 14px; border-radius: 14px; cursor: pointer; font-size: 14px;
}
.favBtn {
  border: 1px solid rgba(0,0,0,0.12); background: white; color: rgba(0,0,0,0.55);
  padding: 12px 14px; border-radius: 14px; cursor: pointer; font-size: 14px;
  transition: all 0.15s;
}
.favBtn.active { color: #e91e63; border-color: #e91e63; }
.favBtn:disabled { opacity: 0.5; cursor: not-allowed; }
.reportBtn {
  border: 1px solid rgba(0,0,0,0.12); background: white; color: rgba(0,0,0,0.4);
  padding: 12px 14px; border-radius: 14px; cursor: pointer; font-size: 14px;
}
.reportBtn:hover { border-color: #b00020; color: #b00020; }
.chatBtn {
  border: 1px solid #1565c0; background: white; color: #1565c0;
  padding: 12px 14px; border-radius: 14px; cursor: pointer; font-size: 14px;
}
.reportReasons { display: grid; gap: 6px; }
.reasonLabel {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; border: 1px solid rgba(0,0,0,0.1); border-radius: 10px;
  cursor: pointer; font-size: 13px;
}
.reasonLabel.sel { border-color: rgba(0,0,0,0.3); background: rgba(0,0,0,0.03); }
.reasonLabel input[type="radio"] { accent-color: black; }
.modal textarea {
  width: 100%; box-sizing: border-box;
  border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  padding: 8px 10px; outline: none; font-family: inherit; font-size: 13px;
  resize: vertical;
}
/* 砍价弹窗 */
.overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.35);
  display: flex; align-items: center; justify-content: center; z-index: 50;
}
.modal {
  background: white; border-radius: 18px; padding: 22px;
  width: 100%; max-width: 400px;
  display: grid; gap: 12px;
}
.modal h3 { margin: 0; font-size: 18px; }
.modal .info { font-size: 14px; color: rgba(0,0,0,0.6); }
.modal label { display: grid; gap: 4px; font-size: 13px; color: rgba(0,0,0,0.7); }
.modal input {
  border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  padding: 8px 10px; outline: none; font-size: 14px;
}
.modalActions { display: flex; gap: 8px; }
.ghost {
  border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 10px 14px; border-radius: 12px; cursor: pointer;
}
.okMsg { color: #2e7d32; font-size: 13px; margin: 0; }
.muted { color: rgba(0,0,0,0.55); }
.error { color: #b00020; }
</style>

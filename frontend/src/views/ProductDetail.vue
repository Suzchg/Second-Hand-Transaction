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

async function load() {
  loading.value = true; error.value = ''
  try { p.value = await api(`/api/products/${route.params.id}`) }
  catch (e) { error.value = e.message || '加载失败' }
  finally { loading.value = false }
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

onMounted(load)
</script>

<template>
  <button class="back" @click="$router.back()">返回</button>
  <p v-if="loading" class="muted">加载中...</p>
  <p v-else-if="error" class="error">{{ error }}</p>

  <div v-else class="wrap">
    <ImageGallery :productId="pid" />
    <div class="meta">
      <div class="title">{{ p.title }}</div>
      <div class="price">¥{{ (p.priceCent / 100).toFixed(2) }}</div>
    </div>
    <div class="desc">
      <div class="h3">描述</div>
      <div class="text">{{ p.description }}</div>
    </div>
    <div v-if="isLoggedIn && !isOwner && p.status === 'ON_SALE'" class="actions">
      <button class="primary" @click="buy">立即购买</button>
      <button class="offerBtn" @click="openOffer">砍价</button>
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

  <AddressPicker v-if="showAddressPicker" @select="onAddress" @close="showAddressPicker = false" />
</template>

<style scoped>
.back { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; }
.wrap { margin-top: 12px; display: grid; gap: 14px; max-width: 600px; }
.hero { height: 300px; border-radius: 18px; background: #f3f3f3 center / cover no-repeat; border: 1px solid rgba(0,0,0,0.08); }
.meta { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.title { font-size: 18px; font-weight: 600; }
.price { font-size: 18px; font-weight: 700; }
.desc { border: 1px solid rgba(0,0,0,0.08); border-radius: 18px; padding: 14px; background: white; }
.h3 { font-weight: 600; margin-bottom: 8px; }
.text { white-space: pre-wrap; line-height: 1.55; color: rgba(0,0,0,0.78); font-size: 14px; }
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

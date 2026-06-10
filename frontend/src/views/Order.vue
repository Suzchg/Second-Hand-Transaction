<script setup>
import { onMounted, ref, computed } from 'vue'
import { api } from '../api.js'
import { useRoute } from 'vue-router'
import { toast } from '../toast.js'
import AddressPicker from '../components/AddressPicker.vue'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const detail = ref(null)
const tracking = ref(null)

const currentUserId = computed(() => Number(localStorage.getItem('userId') || '0'))
const isBuyer = computed(() => detail.value?.order?.buyerId === currentUserId.value)
const isSeller = computed(() => detail.value?.order?.sellerId === currentUserId.value)
const receiverEmpty = computed(() => !detail.value?.order?.receiverName || !detail.value?.order?.receiverPhone || !detail.value?.order?.receiverAddress)
const receiverMissing = computed(() => isBuyer.value && receiverEmpty.value)

// ---- Receiver editing ----
const editingReceiver = ref(false)
const showAddrPicker = ref(false)
const receiverForm = ref({ receiverName: '', receiverPhone: '', receiverAddress: '' })
const receiverSaving = ref(false)
const receiverError = ref('')

function onSelectAddress(addr) {
  receiverForm.value = {
    receiverName: addr.receiverName || '',
    receiverPhone: addr.receiverPhone || '',
    receiverAddress: addr.receiverAddress || '',
  }
  receiverError.value = ''
  showAddrPicker.value = false
  editingReceiver.value = true
}

function startEditReceiver() {
  receiverForm.value = {
    receiverName: detail.value?.order?.receiverName || '',
    receiverPhone: detail.value?.order?.receiverPhone || '',
    receiverAddress: detail.value?.order?.receiverAddress || '',
  }
  receiverError.value = ''
  editingReceiver.value = true
}

async function saveReceiver() {
  if (!receiverForm.value.receiverName.trim()) { receiverError.value = '请输入收货人姓名'; return }
  if (!receiverForm.value.receiverPhone.trim()) { receiverError.value = '请输入收货人电话'; return }
  if (!receiverForm.value.receiverAddress.trim()) { receiverError.value = '请输入收货地址'; return }
  receiverSaving.value = true; receiverError.value = ''
  try {
    await api(`/api/orders/${route.params.id}/receiver`, {
      method: 'PUT',
      body: {
        receiverName: receiverForm.value.receiverName.trim(),
        receiverPhone: receiverForm.value.receiverPhone.trim(),
        receiverAddress: receiverForm.value.receiverAddress.trim(),
      },
    })
    editingReceiver.value = false
    await load()
  } catch (e) { receiverError.value = e.message }
  finally { receiverSaving.value = false }
}

async function load() {
  loading.value = true; error.value = ''
  try { detail.value = await api(`/api/orders/${route.params.id}`) }
  catch (e) { error.value = e.message || '加载失败' }
  finally { loading.value = false }
}
function notifyDone() { window.dispatchEvent(new CustomEvent('action-done')) }

async function pay() { try { await api(`/api/orders/${route.params.id}/pay`, { method: 'POST' }); await load(); notifyDone(); toast.success('支付成功') } catch (e) { toast.error(e.message) } }
async function confirm() { try { await api(`/api/orders/${route.params.id}/confirm`, { method: 'POST' }); await load(); notifyDone(); toast.success('已确认收货') } catch (e) { toast.error(e.message) } }
async function cancel() { try { await api(`/api/orders/${route.params.id}/cancel`, { method: 'POST' }); await load(); notifyDone(); toast.success('订单已取消') } catch (e) { toast.error(e.message) } }

// ---- 发货弹窗 ----
const showShipModal = ref(false)
const shipForm = ref({ carrierCode: '', trackingNo: '' })
const shipError = ref('')
const shipping = ref(false)

function openShip() {
  shipForm.value = { carrierCode: '', trackingNo: '' }
  shipError.value = ''
  showShipModal.value = true
}

async function doShip() {
  if (!shipForm.value.carrierCode.trim()) { shipError.value = '请输入快递公司'; return }
  if (!shipForm.value.trackingNo.trim()) { shipError.value = '请输入快递单号'; return }
  shipping.value = true; shipError.value = ''
  try {
    await api(`/api/orders/${route.params.id}/ship`, {
      method: 'POST',
      body: { carrierCode: shipForm.value.carrierCode.trim(), trackingNo: shipForm.value.trackingNo.trim() },
    })
    showShipModal.value = false
    await load()
    notifyDone()
  } catch (e) { shipError.value = e.message }
  finally { shipping.value = false }
}
// ---- 售后弹窗 ----
const showAfterSaleModal = ref(false)
const afterSaleType = ref('REFUND_NOT_SHIPPED')
const afterSaleReason = ref('')
const afterSaleEvidence = ref('')
const afterSaleError = ref('')
const afterSaleSubmitting = ref(false)

async function submitAfterSale() {
  if (!afterSaleReason.value.trim()) { afterSaleError.value = '请描述售后原因'; return }
  afterSaleSubmitting.value = true; afterSaleError.value = ''
  try {
    await api('/api/after-sale', {
      method: 'POST',
      body: {
        orderId: detail.value.order.id,
        type: afterSaleType.value,
        reason: afterSaleReason.value.trim(),
        buyerEvidence: afterSaleEvidence.value.trim() || null,
      },
    })
    showAfterSaleModal.value = false
    toast.success('售后申请已提交，请等待卖家处理')
    await load()
    await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { afterSaleSubmitting.value = false }
}
async function loadTracking() { try { tracking.value = await api(`/api/shipments/${route.params.id}/track`) } catch (e) { alert(e.message) } }

// ---- 售后 ----
const afterSaleRequests = ref([])
const afterSaleLoading = ref(false)
const showEvidenceUpload = ref(false)
const evidenceText = ref('')
const evidenceUploading = ref(false)
const evidenceUploadRole = ref('') // 'buyer' | 'seller'

// ---- 售后互动操作 ----
const approvingId = ref(null)
const showRejectModal = ref(false)
const rejectNote = ref('')
const rejectingId = ref(null)
const showReturnShipModal = ref(false)
const returnCarrier = ref('')
const returnTracking = ref('')
const returnShippingId = ref(null)
const confirmingReturnId = ref(null)
const rejectingReturnId = ref(null)
const showEscalateModal = ref(false)
const escalateEvidence = ref('')
const escalatingId = ref(null)
const cancellingId = ref(null)
const rejectAfterSaleTarget = ref(null)
const rejectReturnTarget = ref(null)
const returnShipTarget = ref(null)
const escalateTarget = ref(null)

async function loadAfterSale() {
  afterSaleLoading.value = true
  try {
    afterSaleRequests.value = await api(`/api/after-sale/by-order/${route.params.id}`) || []
  } catch { /* 接口可能不存在或无权查看 */ }
  finally { afterSaleLoading.value = false }
}

function openEvidenceUpload(role) {
  evidenceUploadRole.value = role
  evidenceText.value = ''
  showEvidenceUpload.value = true
}

async function uploadEvidence() {
  if (!evidenceText.value.trim()) return
  evidenceUploading.value = true
  try {
    const reqId = afterSaleRequests.value[0]?.id
    if (!reqId) throw new Error('未找到售后单')
    const endpoint = evidenceUploadRole.value === 'seller'
      ? `/api/after-sale/${reqId}/seller-evidence`
      : `/api/after-sale/${reqId}/buyer-evidence`
    await api(endpoint, {
      method: 'POST',
      body: { evidence: evidenceText.value.trim() },
    })
    toast.success('举证已提交')
    showEvidenceUpload.value = false
    await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { evidenceUploading.value = false }
}

// ---- 售后互动操作函数 ----

async function approveAfterSale(req) {
  approvingId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/approve`, { method: 'POST' })
    toast.success('已同意售后')
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { approvingId.value = null }
}

async function rejectAfterSale(req) {
  if (!rejectNote.value.trim()) { toast.warn('请填写拒绝原因'); return }
  rejectingId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/reject`, {
      method: 'POST',
      body: { note: rejectNote.value.trim() },
    })
    toast.success('已拒绝售后')
    showRejectModal.value = false; rejectNote.value = ''
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { rejectingId.value = null }
}

async function submitReturnShip(req) {
  if (!returnCarrier.value.trim()) { toast.warn('请填写快递公司'); return }
  if (!returnTracking.value.trim()) { toast.warn('请填写快递单号'); return }
  returnShippingId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/return-ship`, {
      method: 'POST',
      body: { carrierCode: returnCarrier.value.trim(), trackingNo: returnTracking.value.trim() },
    })
    toast.success('退货信息已提交')
    showReturnShipModal.value = false; returnCarrier.value = ''; returnTracking.value = ''
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { returnShippingId.value = null }
}

async function confirmReturn(req) {
  if (!confirm('确认已收到买家退回的商品？')) return
  confirmingReturnId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/confirm-return`, { method: 'POST' })
    toast.success('已确认退货，款项将退还给买家')
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { confirmingReturnId.value = null }
}

async function rejectReturn(req) {
  if (!rejectNote.value.trim()) { toast.warn('请填写拒收原因'); return }
  rejectingReturnId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/reject-return`, {
      method: 'POST',
      body: { note: rejectNote.value.trim() },
    })
    toast.success('已拒绝收货')
    showRejectModal.value = false; rejectNote.value = ''
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { rejectingReturnId.value = null }
}

async function escalateAfterSale(req) {
  escalatingId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/escalate`, {
      method: 'POST',
      body: { evidence: escalateEvidence.value.trim() || null },
    })
    toast.success('已申请平台介入，请等待平台审核')
    showEscalateModal.value = false; escalateEvidence.value = ''
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { escalatingId.value = null }
}

async function cancelAfterSale(req) {
  if (!confirm('确定取消该售后申请？')) return
  cancellingId.value = req.id
  try {
    await api(`/api/after-sale/${req.id}/cancel`, { method: 'POST' })
    toast.success('售后已取消')
    await load(); await loadAfterSale()
  } catch (e) { toast.error(e.message) }
  finally { cancellingId.value = null }
}

function deadlineLabel(t) {
  if (!t) return ''
  const d = new Date(t).getTime() - Date.now()
  if (d <= 0) return '已超时'
  const days = Math.floor(d / 86400000)
  const hours = Math.floor((d % 86400000) / 3600000)
  return days > 0 ? `剩余 ${days} 天` : `剩余 ${hours} 小时`
}

function typeLabel(t) {
  const m = { REFUND_NOT_SHIPPED: '仅退款（未发货）', REFUND_RECEIVED: '仅退款（已收货）', RETURN_REFUND: '退货退款', PARTIAL_REFUND: '部分退款' }
  return m[t] || t
}
function asStatusLabel(s) {
  const m = { REQUESTED: '待卖家处理', APPROVED: '已同意退货', REJECTED: '已拒绝', RETURN_SHIPPED: '买家已寄回', RETURN_CONFIRMED: '已确认退货', REFUNDED: '已退款', CLOSED: '已关闭', PLATFORM_ARBITRATION: '仲裁中' }
  return m[s] || s
}
function asStatusCls(s) {
  const m = { REQUESTED: 'warn', APPROVED: 'ok', REJECTED: 'err', RETURN_SHIPPED: 'info', RETURN_CONFIRMED: 'info', REFUNDED: 'ok', CLOSED: 'dim', PLATFORM_ARBITRATION: 'active' }
  return m[s] || 'dim'
}
function parseEvidence(raw) {
  if (!raw) return []
  try { const p = JSON.parse(raw); return Array.isArray(p) ? p : [raw] }
  catch { return [raw] }
}
function isImg(u) { return /\.(jpg|jpeg|png|gif|webp|svg)(\?|$)/i.test(u) }
function isVid(u) { return /\.(mp4|mov|avi|webm)(\?|$)/i.test(u) || /\/video\//i.test(u) }

// ---- 评分 ----
const rating = ref(null)
const ratingScore = ref(0)
const ratingComment = ref('')
const ratingSubmitting = ref(false)
const ratingError = ref('')
const ratingSuccess = ref(false)

async function loadRating() {
  try {
    rating.value = await api(`/api/orders/${route.params.id}/rating`)
  } catch { /* ignore */ }
}

async function submitRating() {
  if (!ratingScore.value) { ratingError.value = '请选择评分'; return }
  ratingSubmitting.value = true; ratingError.value = ''
  try {
    rating.value = await api(`/api/orders/${route.params.id}/rate`, {
      method: 'POST',
      body: { score: ratingScore.value, comment: ratingComment.value.trim() || null },
    })
    ratingSuccess.value = true
  } catch (e) { ratingError.value = e.message }
  finally { ratingSubmitting.value = false }
}

function statusLabel(s) {
  const map = {
    WAIT_PAY: '待支付', WAIT_DELIVER: '待发货', WAIT_RECEIVE: '待收货',
    COMPLETED: '已完成（资金托管中）', SETTLED: '已结算', CANCELLED: '已取消', AFTER_SALE: '售后中'
  }
  return map[s] || s
}

function hoverStar(n) { if (!rating.value) ratingScore.value = n }
function setStar(n) { ratingScore.value = n }

onMounted(() => { load(); loadRating(); loadAfterSale() })
</script>

<template>
  <button class="back" @click="$router.back()">← 返回</button>
  <p v-if="loading" class="muted">加载中...</p>
  <p v-else-if="error" class="error">{{ error }}</p>

  <div v-else class="wrap">
    <div class="card">
      <h3>订单 #{{ detail.order.id }}</h3>
      <div class="infoGrid">
        <div><span class="label">状态</span><span class="val status">{{ statusLabel(detail.order.status) }}</span></div>
        <div><span class="label">金额</span><span class="val">¥{{ (detail.order.amountCent / 100).toFixed(2) }}</span></div>
        <div><span class="label">创建</span><span class="val">{{ detail.order.createdAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.paidAt"><span class="label">支付</span><span class="val">{{ detail.order.paidAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.shippedAt"><span class="label">发货</span><span class="val">{{ detail.order.shippedAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.completedAt"><span class="label">确认收货</span><span class="val">{{ detail.order.completedAt?.substring(0,16) }}</span></div>
        <div v-if="detail.order.settledAt"><span class="label">资金结算</span><span class="val">{{ detail.order.settledAt?.substring(0,16) }}</span></div>
      </div>
    </div>

    <!-- 资金托管状态 -->
    <div v-if="detail.fundsInEscrow" class="card escrowCard">
      <h3><AppIcon name="shield" :size="16"/> 资金托管</h3>
      <p>买家已支付的资金由平台托管中，卖家暂未收到款项。</p>
      <p v-if="detail.order.status === 'COMPLETED' && detail.settlementDueAt">
        预计结算时间：<strong>{{ detail.settlementDueAt?.substring(0, 10) }}</strong>（确认收货后7天，期间可发起售后）
      </p>
      <p v-if="detail.order.status === 'SETTLED'" class="settledText">
        ✅ 资金已于 {{ detail.order.settledAt?.substring(0, 10) }} 结算给卖家，订单彻底完结。
      </p>
    </div>
    <div v-if="detail.order.status === 'SETTLED'" class="card escrowCard settled">
      <h3><AppIcon name="check" :size="16"/> 资金已结算</h3>
      <p class="settledText">售后期满，资金已于 {{ detail.order.settledAt?.substring(0, 10) }} 划转给卖家，订单彻底完结。</p>
    </div>

    <div class="card">
      <h3>收货信息</h3>
      <!-- 买家补填收货信息 -->
      <div v-if="editingReceiver" class="receiverForm">
        <label>收货人 <input v-model="receiverForm.receiverName" placeholder="请输入收货人姓名" /></label>
        <label>电话 <input v-model="receiverForm.receiverPhone" placeholder="请输入收货人电话" /></label>
        <label>地址 <input v-model="receiverForm.receiverAddress" placeholder="请输入详细收货地址" /></label>
        <button class="btn sm" @click="showAddrPicker = true">从保存地址选择</button>
        <p v-if="receiverError" class="error">{{ receiverError }}</p>
        <div class="receiverActions">
          <button class="btn primary" :disabled="receiverSaving" @click="saveReceiver">
            {{ receiverSaving ? '保存中...' : '保存' }}
          </button>
          <button v-if="!receiverMissing" class="btn" @click="editingReceiver = false">取消</button>
        </div>
      </div>
      <!-- 收货信息为空且是买家（报价接受后） -->
      <div v-else-if="receiverMissing" class="receiverMissing">
        <p class="warnText">⚠️ 请补填收货信息后再支付</p>
        <div class="receiverActions">
          <button class="btn primary" @click="showAddrPicker = true">从保存地址选择</button>
          <button class="btn" @click="startEditReceiver">手动填写</button>
        </div>
      </div>
      <!-- 收货信息为空且是卖家 -->
      <div v-else-if="receiverEmpty && isSeller" class="receiverEmptySeller">
        <p class="dimText">买家尚未填写收货信息</p>
      </div>
      <!-- 已有收货信息 -->
      <div v-else>
        <div class="infoGrid">
          <div><span class="label">收货人</span><span class="val">{{ detail.order.receiverName }}</span></div>
          <div><span class="label">电话</span><span class="val">{{ detail.order.receiverPhone }}</span></div>
          <div><span class="label">地址</span><span class="val">{{ detail.order.receiverAddress }}</span></div>
        </div>
        <button v-if="isBuyer" class="btn sm editBtn" @click="startEditReceiver">修改</button>
      </div>
    </div>

    <div v-if="detail.shipment" class="card">
      <h3>物流信息</h3>
      <div class="infoGrid">
        <div><span class="label">快递</span><span class="val">{{ detail.shipment.carrierCode }}</span></div>
        <div><span class="label">单号</span><span class="val">{{ detail.shipment.trackingNo }}</span></div>
        <div><span class="label">状态</span><span class="val">{{ detail.shipment.status }}</span></div>
      </div>
      <button class="btn sm" @click="loadTracking">查物流</button>
      <div v-if="tracking" class="trackCard">
        <div v-for="t in tracking.points" :key="t.time" class="trackItem">
          <div class="trackTime">{{ t.time?.substring(0,16) }}</div>
          <div class="trackDesc">{{ t.desc }}</div>
        </div>
      </div>
    </div>

    <div class="actions">
      <button class="btn" @click="load">刷新</button>
      <button v-if="detail.canPay && !receiverEmpty" class="btn primary" @click="pay"><AppIcon name="dollar" :size="16"/> 去支付</button>
      <button v-if="detail.canPay && receiverEmpty" class="btn primary" disabled style="opacity:0.4"><AppIcon name="dollar" :size="16"/> 请先填写收货信息</button>
      <button v-if="detail.canShip" class="btn primary" @click="openShip"><AppIcon name="package" :size="16"/> 发货</button>
      <button v-if="detail.canConfirm" class="btn primary" @click="confirm"><AppIcon name="check" :size="16"/> 确认收货</button>
      <button v-if="detail.canCancel" class="btn danger" @click="cancel">取消订单</button>
      <button v-if="detail.canApplyAfterSale" class="btn warn" @click="showAfterSaleModal = true; afterSaleType = 'REFUND_NOT_SHIPPED'; afterSaleReason = ''; afterSaleEvidence = ''; afterSaleError = ''">申请售后</button>
    </div>

    <!-- 售后请求列表 -->
    <div v-if="afterSaleRequests.length" class="card">
      <h3>售后记录</h3>
      <div v-for="r in afterSaleRequests" :key="r.id" class="asCard">
        <div class="asRow1">
          <span class="asId">售后 #{{ r.id }}</span>
          <span :class="['asTag', asStatusCls(r.status)]">{{ asStatusLabel(r.status) }}</span>
          <span class="asType">{{ typeLabel(r.requestType) }}</span>
          <span class="asAmount">¥{{ ((r.refundAmountCent || 0) / 100).toFixed(2) }}</span>
        </div>
        <div class="asRow2">
          <span>{{ r.reason || '无原因描述' }}</span>
          <span class="asTime">{{ r.createdAt?.substring(0, 16) }}</span>
        </div>
        <div v-if="r.sellerResponse" class="asResponse">卖家回复：{{ r.sellerResponse }}</div>
        <div v-if="r.arbitrationResult" class="asArb">仲裁：{{ r.arbitrationResult }}</div>

        <!-- 买家证据 -->
        <div class="asEvidence">
          <div class="asEvHead">
            <strong>买家举证</strong>
            <button v-if="isBuyer && r.status !== 'REFUNDED' && r.status !== 'CLOSED'" class="link sm" @click="openEvidenceUpload('buyer')">补充举证</button>
          </div>
          <div v-if="parseEvidence(r.buyerEvidence).length" class="evGrid">
            <div v-for="(item, i) in parseEvidence(r.buyerEvidence)" :key="'be'+i" class="evItem">
              <img v-if="isImg(item)" :src="item" class="evImg" loading="lazy" />
              <video v-else-if="isVid(item)" :src="item" controls class="evVid"></video>
              <p v-else class="evText">{{ item }}</p>
            </div>
          </div>
          <p v-else class="muted sm">暂无买家举证</p>
        </div>

        <!-- 卖家证据 -->
        <div class="asEvidence">
          <div class="asEvHead">
            <strong>卖家举证</strong>
            <button v-if="isSeller && r.status !== 'REFUNDED' && r.status !== 'CLOSED'" class="link sm" @click="openEvidenceUpload('seller')">上传举证</button>
          </div>
          <div v-if="parseEvidence(r.sellerEvidence).length" class="evGrid">
            <div v-for="(item, i) in parseEvidence(r.sellerEvidence)" :key="'se'+i" class="evItem">
              <img v-if="isImg(item)" :src="item" class="evImg" loading="lazy" />
              <video v-else-if="isVid(item)" :src="item" controls class="evVid"></video>
              <p v-else class="evText">{{ item }}</p>
            </div>
          </div>
          <p v-else class="muted sm">暂无卖家举证</p>
        </div>

        <!-- 进度时间线 -->
        <div class="asTimeline">
          <span class="tlStep" :class="{ done: r.requestedAt }">发起</span>
          <span class="tlArrow">→</span>
          <span class="tlStep" :class="{ done: r.handledAt }">处理</span>
          <span class="tlArrow">→</span>
          <span class="tlStep" :class="{ done: r.returnedAt || r.status === 'REFUNDED' }">{{ r.requestType === 'RETURN_REFUND' ? '寄回' : '退款' }}</span>
          <span class="tlArrow">→</span>
          <span class="tlStep" :class="{ done: r.status === 'REFUNDED' || r.status === 'CLOSED' }">{{ r.status === 'CLOSED' ? '关闭' : '完结' }}</span>
        </div>

        <!-- 截止时间 -->
        <div v-if="r.deadlineAt" class="asDeadline" :class="{ overdue: deadlineLabel(r.deadlineAt) === '已超时' }">
          ⏱ {{ deadlineLabel(r.deadlineAt) }}
          <span v-if="deadlineLabel(r.deadlineAt) !== '已超时'" class="deadlineDate">（{{ r.deadlineAt?.substring(0, 16) }}）</span>
        </div>

        <!-- 退货物流信息 -->
        <div v-if="r.returnCarrierCode" class="asReturnInfo">
          📦 退货快递：{{ r.returnCarrierCode }} {{ r.returnTrackingNo || '' }}
        </div>

        <!-- 卖家操作：同意/拒绝 -->
        <div v-if="isSeller && (r.status === 'REQUESTED' || r.status === 'PLATFORM_ARBITRATION')" class="asActions">
          <button class="btn primary sm" :disabled="approvingId === r.id" @click="approveAfterSale(r)">
            {{ approvingId === r.id ? '处理中...' : '同意售后' }}
          </button>
          <button class="btn danger sm" @click="showRejectModal = true; rejectNote = ''; rejectAfterSaleTarget = r">
            拒绝售后
          </button>
        </div>

        <!-- 买家操作：寄回退货 -->
        <div v-if="isBuyer && r.status === 'APPROVED' && r.requestType === 'RETURN_REFUND'" class="asActions">
          <button class="btn primary sm" @click="showReturnShipModal = true; returnCarrier = ''; returnTracking = ''; returnShipTarget = r">
            填写退货信息
          </button>
        </div>

        <!-- 卖家操作：确认/拒收退货 -->
        <div v-if="isSeller && r.status === 'RETURN_SHIPPED'" class="asActions">
          <button class="btn primary sm" :disabled="confirmingReturnId === r.id" @click="confirmReturn(r)">
            {{ confirmingReturnId === r.id ? '处理中...' : '确认收到退货' }}
          </button>
          <button class="btn danger sm" @click="showRejectModal = true; rejectNote = ''; rejectReturnTarget = r">
            退货有问题
          </button>
        </div>

        <!-- 买家操作：申请平台介入 -->
        <div v-if="isBuyer && r.status === 'REJECTED'" class="asActions">
          <button class="btn warn sm" :disabled="escalatingId === r.id" @click="showEscalateModal = true; escalateEvidence = ''; escalateTarget = r">
            {{ escalatingId === r.id ? '提交中...' : '申请平台介入' }}
          </button>
        </div>

        <!-- 买家操作：取消售后 -->
        <div v-if="isBuyer && r.status !== 'REFUNDED' && r.status !== 'CLOSED'" class="asActions">
          <button class="btn sm" :disabled="cancellingId === r.id" @click="cancelAfterSale(r)">
            {{ cancellingId === r.id ? '取消中...' : '取消售后' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 拒绝售后弹窗 -->
    <div v-if="showRejectModal" class="overlay" @click.self="showRejectModal = false">
      <div class="modal">
        <h3>拒绝说明</h3>
        <label>请填写拒绝原因
          <textarea v-model="rejectNote" rows="3" placeholder="例如：商品与描述一致，不符合退款条件..." class="textarea"></textarea>
        </label>
        <div class="modalActions">
          <button class="btn danger" :disabled="rejectingId || rejectingReturnId" @click="rejectReturnTarget ? rejectReturn(rejectReturnTarget) : rejectAfterSale(rejectAfterSaleTarget)">
            {{ (rejectingId || rejectingReturnId) ? '提交中...' : '确认拒绝' }}
          </button>
          <button class="btn" @click="showRejectModal = false; rejectReturnTarget = null; rejectAfterSaleTarget = null">取消</button>
        </div>
      </div>
    </div>

    <!-- 退货信息弹窗 -->
    <div v-if="showReturnShipModal" class="overlay" @click.self="showReturnShipModal = false">
      <div class="modal">
        <h3>填写退货信息</h3>
        <label>快递公司 <input v-model="returnCarrier" placeholder="例如：顺丰、圆通、中通" /></label>
        <label>快递单号 <input v-model="returnTracking" placeholder="请输入快递单号" /></label>
        <div class="modalActions">
          <button class="btn primary" :disabled="returnShippingId" @click="submitReturnShip(returnShipTarget)">
            {{ returnShippingId ? '提交中...' : '确认寄回' }}
          </button>
          <button class="btn" @click="showReturnShipModal = false; returnShipTarget = null">取消</button>
        </div>
      </div>
    </div>

    <!-- 平台介入弹窗 -->
    <div v-if="showEscalateModal" class="overlay" @click.self="showEscalateModal = false">
      <div class="modal">
        <h3>申请平台介入</h3>
        <p class="hintText">平台将在 3~5 个工作日内审核并出具裁决结果，裁决为最终结果。</p>
        <label>补充说明/证据（选填）
          <textarea v-model="escalateEvidence" rows="3" placeholder="补充说明或证据材料..." class="textarea"></textarea>
        </label>
        <div class="modalActions">
          <button class="btn warn" :disabled="escalatingId" @click="escalateAfterSale(escalateTarget)">
            {{ escalatingId ? '提交中...' : '确认申请平台介入' }}
          </button>
          <button class="btn" @click="showEscalateModal = false; escalateTarget = null">取消</button>
        </div>
      </div>
    </div>

    <!-- 举证上传弹窗 -->
    <div v-if="showEvidenceUpload" class="overlay" @click.self="showEvidenceUpload = false">
      <div class="modal">
        <h3>{{ evidenceUploadRole === 'seller' ? '卖家上传举证' : '买家补充举证' }}</h3>
        <p class="hintText">请提供证据材料，支持图片URL、视频URL或文字描述。多条可用JSON数组格式：["url1", "url2", "说明文字"]</p>
        <textarea v-model="evidenceText" rows="5" placeholder="例如：&#10;https://example.com/开箱视频.mp4&#10;https://example.com/商品照片.jpg&#10;或直接输入文字描述" class="textarea" />
        <div class="modalActions">
          <button class="btn primary" :disabled="evidenceUploading || !evidenceText.trim()" @click="uploadEvidence">
            {{ evidenceUploading ? '上传中...' : '提交举证' }}
          </button>
          <button class="btn" @click="showEvidenceUpload = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 评分 -->
    <div v-if="detail.order.status === 'COMPLETED'" class="card">
      <h3>评分</h3>
      <template v-if="rating">
        <div class="ratingDone">
          <span class="starsDisplay">{{ '★'.repeat(rating.score) }}{{ '☆'.repeat(5 - rating.score) }}</span>
          <span class="ratingScore">{{ rating.score }} 分</span>
          <p v-if="rating.comment" class="ratingText">{{ rating.comment }}</p>
        </div>
      </template>
      <template v-else-if="ratingSuccess">
        <p class="okMsg">✅ 评分已提交</p>
      </template>
      <template v-else>
        <div class="ratingForm">
          <div class="starsRow">
            <span v-for="n in 5" :key="n"
              :class="['star', { on: n <= ratingScore }]"
              @click="setStar(n)" @mouseenter="hoverStar(n)"
            >{{ n <= ratingScore ? '★' : '☆' }}</span>
          </div>
          <textarea v-model="ratingComment" rows="2" placeholder="写点评价（选填）" />
          <button class="btn primary sm" :disabled="ratingSubmitting" @click="submitRating">
            {{ ratingSubmitting ? '提交中...' : '提交评分' }}
          </button>
          <p v-if="ratingError" class="error">{{ ratingError }}</p>
        </div>
      </template>
    </div>

    <div v-if="detail.events?.length" class="card">
      <h3>状态时间线</h3>
      <div v-for="e in detail.events" :key="e.id" class="eventItem">
        <div class="eventTime">{{ e.createdAt?.substring(0,16) }}</div>
        <div class="eventDot" />
        <div class="eventNote">{{ e.note }}</div>
      </div>
    </div>
  </div>

  <!-- Address picker modal -->
  <AddressPicker v-if="showAddrPicker" @select="onSelectAddress" @close="showAddrPicker = false" />

  <!-- 售后申请弹窗 -->
  <div v-if="showAfterSaleModal" class="overlay" @click.self="showAfterSaleModal = false">
    <div class="modal">
      <h3>申请售后</h3>
      <label>售后类型
        <select v-model="afterSaleType" class="sel">
          <option value="REFUND_NOT_SHIPPED">仅退款</option>
          <option value="RETURN_REFUND">退货退款</option>
          <option value="REFUND_RECEIVED">仅退款（已收货不退货）</option>
        </select>
      </label>
      <label>售后原因 <textarea v-model="afterSaleReason" rows="3" placeholder="请详细描述售后原因..." /></label>
      <label>举证材料（选填） <textarea v-model="afterSaleEvidence" rows="3" placeholder="提供图片URL、视频URL或文字描述。多条用JSON数组：&quot;[&quot;url1&quot;, &quot;url2&quot;]&quot;" /></label>
      <p v-if="afterSaleError" class="error">{{ afterSaleError }}</p>
      <div class="modalActions">
        <button class="btn primary" :disabled="afterSaleSubmitting" @click="submitAfterSale">
          {{ afterSaleSubmitting ? '提交中...' : '提交申请' }}
        </button>
        <button class="btn" @click="showAfterSaleModal = false">取消</button>
      </div>
    </div>
  </div>

  <!-- 发货弹窗 -->
  <div v-if="showShipModal" class="overlay" @click.self="showShipModal = false">
    <div class="modal">
      <h3>填写发货信息</h3>
      <label>快递公司 <input v-model="shipForm.carrierCode" placeholder="例如：SF、YTO、ZTO" /></label>
      <label>快递单号 <input v-model="shipForm.trackingNo" placeholder="请输入快递单号" /></label>
      <p v-if="shipError" class="error">{{ shipError }}</p>
      <div class="modalActions">
        <button class="btn primary" :disabled="shipping" @click="doShip">
          {{ shipping ? '发货中...' : '确认发货' }}
        </button>
        <button class="btn" @click="showShipModal = false">取消</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
  transition: all var(--transition-fast);
}

.back:hover { border-color: var(--border-strong); color: var(--text-primary); }

.wrap { margin-top: var(--space-md); display: grid; gap: var(--space-md); max-width: 620px; }

.card {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

h3 { margin: 0 0 var(--space-md); font-size: 15px; font-weight: 600; }

.infoGrid { display: grid; gap: var(--space-xs); }

.label { font-size: 12px; color: var(--text-tertiary); margin-right: var(--space-sm); }

.val { font-size: 14px; color: var(--text-primary); }

.val.status { font-weight: 600; color: var(--info); }

.actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }

.btn {
  padding: 8px 16px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-primary);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.btn:hover { border-color: var(--border-strong); }

.btn.sm { padding: 5px 10px; font-size: 12px; margin-top: 6px; }

.editBtn { margin-top: var(--space-sm); }

.receiverForm { display: grid; gap: var(--space-sm); }

.receiverForm label {
  display: grid;
  gap: 3px;
  font-size: 13px;
  color: var(--text-secondary);
}

.receiverForm input {
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-size: 14px;
  background: var(--bg-tertiary);
}

.receiverActions { display: flex; gap: var(--space-sm); margin-top: 4px; }

.receiverMissing { display: grid; gap: var(--space-sm); }

.receiverEmptySeller { padding: var(--space-sm) 0; }

.warnText { color: var(--warning); font-size: 14px; margin: 0; }

.dimText { color: var(--text-tertiary); font-size: 14px; margin: 0; }

.btn.primary {
  background: var(--brand-gradient);
  color: white;
  border: none;
  font-weight: 600;
  box-shadow: var(--shadow-brand);
}

.btn.primary:hover { opacity: 0.9; }

.btn.danger { color: var(--error); border-color: var(--error-border); }
.btn.danger:hover { background: var(--error-bg); }

.btn.warn { color: var(--brand-darker); border-color: var(--brand-dark); }
.btn.warn:hover { background: var(--brand-light); }

.trackCard {
  margin-top: var(--space-sm);
  padding: var(--space-md);
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
}

.trackItem { display: flex; gap: var(--space-md); padding: 3px 0; font-size: 13px; }

.trackTime { color: var(--text-tertiary); white-space: nowrap; min-width: 120px; }
.trackDesc { color: var(--text-secondary); }

.eventItem { display: flex; align-items: center; gap: var(--space-md); padding: 5px 0; font-size: 13px; }

.eventTime { color: var(--text-tertiary); min-width: 120px; }

.eventDot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border-strong);
  flex-shrink: 0;
}

.eventNote { color: var(--text-secondary); }

.muted { color: var(--text-tertiary); }

.error { color: var(--error); }

.starsRow { display: flex; gap: 2px; margin-bottom: var(--space-sm); }

.star {
  font-size: 28px;
  cursor: pointer;
  color: var(--border-strong);
  user-select: none;
  transition: color 0.1s;
}

.star.on { color: var(--brand-dark); }

.ratingDone { display: grid; gap: 4px; }

.starsDisplay { font-size: 22px; color: var(--brand-dark); letter-spacing: 2px; }

.ratingScore { font-size: 14px; font-weight: 600; color: var(--text-primary); }

.ratingText { margin: 0; font-size: 13px; color: var(--text-secondary); }

.ratingForm { display: grid; gap: var(--space-sm); }

.ratingForm textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-family: inherit;
  font-size: 13px;
  resize: vertical;
  background: var(--bg-tertiary);
}

.okMsg { color: var(--success); font-size: 14px; margin: 0; }

/* Ship modal */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: var(--bg-primary);
  border-radius: var(--radius-xl);
  padding: var(--space-2xl);
  width: 100%;
  max-width: 420px;
  display: grid;
  gap: var(--space-md);
  box-shadow: var(--shadow-xl);
}

.modal h3 { margin: 0; font-size: 18px; }

.modal label { display: grid; gap: 4px; font-size: 13px; color: var(--text-secondary); }

.modal input, .modal textarea, .modal .sel {
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 9px 12px;
  font-size: 14px;
  font-family: inherit;
  background: var(--bg-tertiary);
}

.modal .sel { background: var(--bg-tertiary); width: 100%; cursor: pointer; }

.modal textarea { resize: vertical; min-height: 60px; }

.modalActions { display: flex; gap: var(--space-sm); }

/* Escrow */
.escrowCard { background: var(--brand-light); border-color: rgba(14, 181, 166, 0.3); }
.escrowCard h3 { color: var(--brand-darker); }
.escrowCard p { margin: 4px 0; font-size: 13px; color: var(--text-secondary); }
.escrowCard.settled { background: var(--success-bg); border-color: var(--success-border); }
.escrowCard.settled h3 { color: var(--success); }
.settledText { color: var(--success) !important; }

/* ---- 售后 ---- */
.asCard {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
  display: grid;
  gap: var(--space-sm);
}

.asRow1 { display: flex; gap: var(--space-sm); align-items: center; flex-wrap: wrap; }
.asId { font-weight: 600; font-size: 13px; }
.asType { font-size: 11px; color: var(--text-tertiary); }
.asAmount { font-weight: 600; font-size: 14px; color: var(--accent); }
.asRow2 { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-tertiary); }
.asTime { font-size: 11px; color: var(--text-disabled); }
.asResponse {
  font-size: 12px;
  color: var(--text-secondary);
  padding: 4px 8px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-xs);
}
.asArb {
  font-size: 12px;
  color: #7c3aed;
  padding: 4px 8px;
  background: #f5f3ff;
  border-radius: var(--radius-xs);
}

.asEvidence { margin-top: 4px; }
.asEvHead { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.asEvHead strong { font-size: 12px; color: var(--text-tertiary); }
.link.sm { font-size: 11px; color: var(--info); background: none; border: 0; cursor: pointer; padding: 0; }
.link.sm:hover { text-decoration: underline; }

.evGrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 6px; }
.evItem { border: 1px solid var(--border-light); border-radius: var(--radius-sm); overflow: hidden; }
.evImg { width: 100%; height: 80px; object-fit: cover; display: block; }
.evVid { width: 100%; height: 80px; object-fit: contain; background: black; }
.evText { padding: 6px 8px; font-size: 11px; color: var(--text-tertiary); margin: 0; word-break: break-all; }

.asTimeline { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-disabled); padding-top: 4px; }
.tlStep { padding: 1px 6px; border-radius: var(--radius-xs); }
.tlStep.done { color: var(--success); font-weight: 500; }
.tlArrow { font-size: 10px; }
.sm { font-size: 11px; }
.muted.sm { font-size: 11px; }
.hintText { font-size: 12px; color: var(--text-tertiary); margin: 0 0 8px; line-height: 1.6; }

/* 售后标签 */
.asTag { font-size: 11px; padding: 2px 8px; border-radius: var(--radius-xs); font-weight: 500; }
.asTag.warn { background: var(--warning-bg); color: var(--warning); }
.asTag.ok { background: var(--success-bg); color: var(--success); }
.asTag.err { background: var(--error-bg); color: var(--error); }
.asTag.dim { background: var(--bg-secondary); color: var(--text-tertiary); }
.asTag.info { background: var(--info-bg); color: var(--info); }
.asTag.active { background: #f5f3ff; color: #7c3aed; }

/* 售后操作按钮区 */
.asActions { display: flex; gap: var(--space-sm); margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--border-light); }

/* 截止时间 */
.asDeadline { font-size: 12px; color: var(--warning); padding: 4px 0; }
.asDeadline.overdue { color: var(--error); }
.deadlineDate { color: var(--text-tertiary); font-size: 11px; }

/* 退货物流信息 */
.asReturnInfo { font-size: 12px; color: var(--text-secondary); padding: 4px 8px; background: var(--bg-tertiary); border-radius: var(--radius-xs); }
</style>

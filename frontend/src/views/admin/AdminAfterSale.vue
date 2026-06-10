<script setup>
import { onMounted, ref, computed } from 'vue'
import { api } from '../../api.js'
import { toast } from '../../toast.js'

const requests = ref([])
const loading = ref(true)
const error = ref('')
const filterStatus = ref('all')
const filterType = ref('all')
const expandedId = ref(null)

// 仲裁表单
const arbitratingId = ref(null)
const arbResult = ref('FULL_REFUND')
const arbResponsibility = ref('SELLER')
const arbShippingPaidBy = ref('SELLER')
const arbShippingCost = ref('')
const arbPartialRefund = ref('')
const arbNote = ref('')

// 分页
const page = ref(0)
const size = ref(100)
const total = ref(0)

async function load() {
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ page: page.value, size: size.value })
    if (filterStatus.value !== 'all') params.set('status', filterStatus.value)
    if (filterType.value !== 'all') params.set('type', filterType.value)
    const res = await api(`/api/admin/after-sale?${params}`)
    // Spring Page returns { content, totalElements, ... }
    if (res && res.content) {
      requests.value = res.content
      total.value = res.totalElements || 0
    } else if (Array.isArray(res)) {
      requests.value = res
      total.value = res.length
    } else {
      requests.value = []
      total.value = 0
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function toggleExpand(id) {
  expandedId.value = expandedId.value === id ? null : id
}

// ---- 映射 ----
const typeLabel = (t) => ({
  REFUND_NOT_SHIPPED: '仅退款（未发货）',
  REFUND_RECEIVED: '仅退款（已收货）',
  RETURN_REFUND: '退货退款',
  PARTIAL_REFUND: '部分退款',
}[t] || t)

const statusLabel = (s) => ({
  REQUESTED: '待卖家处理',
  APPROVED: '已同意退货',
  REJECTED: '已拒绝',
  RETURN_SHIPPED: '买家已寄回',
  RETURN_CONFIRMED: '已确认退货',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
  PLATFORM_ARBITRATION: '仲裁中',
}[s] || s)

const statusCls = (s) => ({
  REQUESTED: 'warn',
  APPROVED: 'info',
  REJECTED: 'err',
  RETURN_SHIPPED: 'info',
  RETURN_CONFIRMED: 'ok',
  REFUNDED: 'ok',
  CLOSED: 'dim',
  PLATFORM_ARBITRATION: 'active',
}[s] || 'dim')

const respLabel = (r) => ({ BUYER: '买家', SELLER: '卖家', LOGISTICS: '物流' }[r] || r)
const respCls = (r) => ({ BUYER: 'warn', SELLER: 'err', LOGISTICS: 'info' }[r] || 'dim')

// 仲裁预览
const arbPreview = computed(() => {
  const lines = []
  const r = arbResult.value
  const resp = arbResponsibility.value
  const ship = arbShippingPaidBy.value
  const cost = parseFloat(arbShippingCost.value) || 0

  if (r === 'FULL_REFUND') lines.push('✅ 全额退款给买家')
  else if (r === 'PARTIAL_REFUND') {
    const pr = parseFloat(arbPartialRefund.value) || 0
    lines.push(`✅ 部分退款 ¥${pr.toFixed(2)} 给买家`)
  } else if (r === 'RETURN_REFUND') lines.push('🔄 买家退货，卖家退款')
  else if (r === 'DISMISS') lines.push('❌ 驳回售后申请')

  if (resp === 'SELLER') lines.push('📌 卖家为责任方')
  else if (resp === 'BUYER') lines.push('📌 买家为责任方')
  else if (resp === 'LOGISTICS') lines.push('📌 物流方责任')

  if (cost > 0) {
    const who = ship === 'SELLER' ? '卖家' : ship === 'BUYER' ? '买家' : '平台'
    lines.push(`🚚 ${who}承担运费 ¥${cost.toFixed(2)}`)
  }
  return lines
})

// ---- 操作 ----
async function arbitrate(req) {
  if (!arbNote.value.trim()) { toast.warn('请填写仲裁说明'); return }
  if (!arbResponsibility.value) { toast.warn('请选择责任方'); return }
  arbitratingId.value = req.id
  try {
    await api(`/api/admin/after-sale/${req.id}/arbitrate`, {
      method: 'POST',
      body: {
        result: arbResult.value,
        responsibility: arbResponsibility.value,
        shippingPaidBy: arbShippingPaidBy.value || null,
        shippingCostCent: arbShippingCost.value ? Math.round(parseFloat(arbShippingCost.value) * 100) : null,
        partialRefundCent: arbPartialRefund.value ? Math.round(parseFloat(arbPartialRefund.value) * 100) : null,
        note: arbNote.value.trim(),
      },
    })
    toast.success('仲裁完成')
    resetArbForm()
    expandedId.value = null
    await load()
  } catch (e) { toast.error(e.message) }
  finally { arbitratingId.value = null }
}

function resetArbForm() {
  arbResult.value = 'FULL_REFUND'
  arbResponsibility.value = 'SELLER'
  arbShippingPaidBy.value = 'SELLER'
  arbShippingCost.value = ''
  arbPartialRefund.value = ''
  arbNote.value = ''
}

// 解析证据 JSON（兼容字符串数组和旧格式）
function parseEvidence(raw) {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed
    return [raw] // 旧格式：纯文本
  } catch {
    return [raw]
  }
}

function isImageUrl(url) {
  return /\.(jpg|jpeg|png|gif|webp|svg)(\?|$)/i.test(url)
}

function isVideoUrl(url) {
  return /\.(mp4|mov|avi|webm)(\?|$)/i.test(url) || /\/video\//i.test(url)
}

// 计算退款金额展示
function refundAmountDisplay(req) {
  const amt = (req.refundAmountCent || 0) / 100
  // 判断是否为全额退款
  return `¥${amt.toFixed(2)}`
}

function formatTime(t) {
  return t ? t.substring(0, 16).replace('T', ' ') : ''
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>售后管理</h2>
      <span v-if="total" class="count">{{ total }} 条</span>
      <button class="ghost" @click="load">刷新</button>
    </div>

    <!-- 筛选器 -->
    <div class="filters">
      <div class="filterGroup">
        <span class="filterLabel">状态：</span>
        <button :class="['tab', { active: filterStatus === 'all' }]" @click="filterStatus = 'all'; load()">全部</button>
        <button :class="['tab', { active: filterStatus === 'REQUESTED' }]" @click="filterStatus = 'REQUESTED'; load()">待处理</button>
        <button :class="['tab', { active: filterStatus === 'REJECTED' }]" @click="filterStatus = 'REJECTED'; load()">已拒绝</button>
        <button :class="['tab', { active: filterStatus === 'PLATFORM_ARBITRATION' }]" @click="filterStatus = 'PLATFORM_ARBITRATION'; load()">仲裁中</button>
        <button :class="['tab', { active: filterStatus === 'REFUNDED' }]" @click="filterStatus = 'REFUNDED'; load()">已退款</button>
        <button :class="['tab', { active: filterStatus === 'CLOSED' }]" @click="filterStatus = 'CLOSED'; load()">已关闭</button>
      </div>
      <div class="filterGroup">
        <span class="filterLabel">类型：</span>
        <button :class="['tab', { active: filterType === 'all' }]" @click="filterType = 'all'; load()">全部</button>
        <button :class="['tab', { active: filterType === 'RETURN_REFUND' }]" @click="filterType = 'RETURN_REFUND'; load()">退货退款</button>
        <button :class="['tab', { active: filterType === 'REFUND_NOT_SHIPPED' }]" @click="filterType = 'REFUND_NOT_SHIPPED'; load()">仅退款(未发货)</button>
        <button :class="['tab', { active: filterType === 'REFUND_RECEIVED' }]" @click="filterType = 'REFUND_RECEIVED'; load()">仅退款(已收货)</button>
        <button :class="['tab', { active: filterType === 'PARTIAL_REFUND' }]" @click="filterType = 'PARTIAL_REFUND'; load()">部分退款</button>
      </div>
    </div>

    <!-- 加载/错误 -->
    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <!-- 列表 -->
    <div v-else-if="requests.length" class="list">
      <div v-for="r in requests" :key="r.id" class="card">
        <!-- 摘要行 -->
        <div class="row1" @click="toggleExpand(r.id)">
          <span class="id">售后 #{{ r.id }}</span>
          <span class="orderId">订单 #{{ r.orderId }}</span>
          <span :class="['tag', statusCls(r.status)]">{{ statusLabel(r.status) }}</span>
          <span class="typeTag">{{ typeLabel(r.requestType) }}</span>
          <span class="amount">{{ refundAmountDisplay(r) }}</span>
          <span v-if="r.responsibility" :class="['tag', 'resp', respCls(r.responsibility)]">
            责任：{{ respLabel(r.responsibility) }}
          </span>
          <span class="expandHint">{{ expandedId === r.id ? '收起 ▲' : '展开 ▼' }}</span>
        </div>

        <div class="row2">
          <span>买家 #{{ r.buyerId }} → 卖家 #{{ r.sellerId }}</span>
          <span class="time">{{ formatTime(r.createdAt) }}</span>
          <span v-if="r.deadlineAt" class="deadline">截止：{{ formatTime(r.deadlineAt) }}</span>
        </div>

        <!-- 展开详情 -->
        <div v-if="expandedId === r.id" class="detail">
          <!-- 基本信息 -->
          <div class="detailSection">
            <h4>售后信息</h4>
            <div class="infoGrid">
              <div><strong>售后类型：</strong>{{ typeLabel(r.requestType) }}</div>
              <div><strong>退款金额：</strong>{{ refundAmountDisplay(r) }}</div>
              <div><strong>售后原因：</strong>{{ r.reason || '无' }}</div>
              <div v-if="r.sellerResponse"><strong>卖家回复：</strong>{{ r.sellerResponse }}</div>
              <div v-if="r.returnCarrierCode"><strong>退货快递：</strong>{{ r.returnCarrierCode }} {{ r.returnTrackingNo || '' }}</div>
              <div v-if="r.responsibility"><strong>责任归属：</strong><span :class="['tag', 'resp', respCls(r.responsibility)]">{{ respLabel(r.responsibility) }}</span></div>
              <div v-if="r.shippingPaidBy"><strong>运费承担：</strong>{{ r.shippingPaidBy === 'SELLER' ? '卖家承担' : r.shippingPaidBy === 'BUYER' ? '买家承担' : '平台承担' }}
                <span v-if="r.shippingCostCent"> ¥{{ (r.shippingCostCent / 100).toFixed(2) }}</span>
              </div>
            </div>
          </div>

          <!-- 时间线 -->
          <div class="detailSection">
            <h4>时间线</h4>
            <div class="timeline">
              <div class="tlItem"><span class="tlDot"></span> 发起：{{ formatTime(r.requestedAt) || formatTime(r.createdAt) }}</div>
              <div v-if="r.handledAt" class="tlItem"><span class="tlDot done"></span> 处理：{{ formatTime(r.handledAt) }}</div>
              <div v-if="r.returnedAt" class="tlItem"><span class="tlDot done"></span> 买家寄回：{{ formatTime(r.returnedAt) }}</div>
              <div v-if="r.refundedAt" class="tlItem"><span class="tlDot done"></span> 退款：{{ formatTime(r.refundedAt) }}</div>
              <div v-if="r.closedAt" class="tlItem"><span class="tlDot closed"></span> 关闭：{{ formatTime(r.closedAt) }}</div>
            </div>
          </div>

          <!-- 买家举证 -->
          <div class="detailSection">
            <h4>买家举证</h4>
            <div v-if="parseEvidence(r.buyerEvidence).length" class="evidenceGrid">
              <div v-for="(item, i) in parseEvidence(r.buyerEvidence)" :key="'be'+i" class="evidenceItem">
                <img v-if="isImageUrl(item)" :src="item" class="evidenceImg" loading="lazy" />
                <video v-else-if="isVideoUrl(item)" :src="item" controls class="evidenceVideo"></video>
                <p v-else class="evidenceText">{{ item }}</p>
              </div>
            </div>
            <p v-else class="muted">暂无买家举证</p>
          </div>

          <!-- 卖家举证 -->
          <div class="detailSection">
            <h4>卖家举证</h4>
            <div v-if="parseEvidence(r.sellerEvidence).length" class="evidenceGrid">
              <div v-for="(item, i) in parseEvidence(r.sellerEvidence)" :key="'se'+i" class="evidenceItem">
                <img v-if="isImageUrl(item)" :src="item" class="evidenceImg" loading="lazy" />
                <video v-else-if="isVideoUrl(item)" :src="item" controls class="evidenceVideo"></video>
                <p v-else class="evidenceText">{{ item }}</p>
              </div>
            </div>
            <p v-else class="muted">暂无卖家举证</p>
          </div>

          <!-- 仲裁结果 -->
          <div v-if="r.arbitrationResult" class="detailSection arbSection">
            <h4>仲裁结果</h4>
            <p class="arbText">{{ r.arbitrationResult }}</p>
          </div>

          <!-- 仲裁操作（仅在仲裁中状态显示） -->
          <div v-if="r.status === 'PLATFORM_ARBITRATION'" class="arbitrateBox">
            <h4>平台裁决</h4>
            <div class="arbForm">
              <div class="arbRow">
                <label>裁决结果：</label>
                <select v-model="arbResult" class="sel">
                  <option value="FULL_REFUND">全额退款</option>
                  <option value="PARTIAL_REFUND">部分折价退款</option>
                  <option value="RETURN_REFUND">退货退款</option>
                  <option value="DISMISS">驳回售后</option>
                </select>
              </div>

              <div class="arbRow">
                <label>责任方：</label>
                <select v-model="arbResponsibility" class="sel">
                  <option value="SELLER">卖家责任</option>
                  <option value="BUYER">买家责任</option>
                  <option value="LOGISTICS">物流责任</option>
                </select>
              </div>

              <div v-if="arbResult === 'PARTIAL_REFUND'" class="arbRow">
                <label>部分退款金额 (¥)：</label>
                <input v-model="arbPartialRefund" placeholder="如 50.00" class="inp" />
              </div>

              <div class="arbRow">
                <label>运费承担：</label>
                <select v-model="arbShippingPaidBy" class="sel">
                  <option value="SELLER">卖家承担</option>
                  <option value="BUYER">买家自理</option>
                  <option value="PLATFORM">平台承担</option>
                </select>
                <input v-model="arbShippingCost" placeholder="运费金额 ¥" class="inp short" />
              </div>

              <div class="arbRow">
                <label>仲裁说明：</label>
                <textarea v-model="arbNote" placeholder="详细说明裁决理由（必填）" class="textarea" rows="3"></textarea>
              </div>

              <!-- 预览 -->
              <div v-if="arbNote" class="arbPreview">
                <div v-for="(line, i) in arbPreview" :key="'prev'+i">{{ line }}</div>
              </div>

              <div class="arbActions">
                <button class="btn primary" :disabled="arbitratingId === r.id" @click="arbitrate(r)">
                  {{ arbitratingId === r.id ? '提交中...' : '确认裁决' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <p v-else class="muted">暂无售后记录</p>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: flex-start; align-items: center; gap: 12px; margin-bottom: 14px; }
h2 { margin: 0; font-size: 18px; color: var(--text-primary); }
h4 { margin: 0 0 8px; font-size: 14px; color: var(--text-secondary); }
.count { font-size: 12px; color: var(--text-tertiary); }
.ghost {
  border: 1px solid var(--border-default); background: var(--bg-primary);
  padding: 5px 10px; border-radius: 8px; cursor: pointer; font-size: 12px; color: var(--text-secondary);
}

/* 筛选 */
.filters { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
.filterGroup { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.filterLabel { font-size: 12px; color: var(--text-tertiary); min-width: 42px; }
.tab {
  border: 1px solid var(--border-default); background: var(--bg-primary);
  padding: 4px 10px; border-radius: 6px; cursor: pointer; font-size: 12px; color: var(--text-secondary);
}
.tab.active { background: var(--text-primary); color: var(--bg-primary); border-color: var(--text-primary); }

/* 列表 */
.list { display: grid; gap: 10px; }
.card {
  border: 1px solid var(--border-light); border-radius: 12px; padding: 12px 14px;
  background: var(--bg-primary); display: grid; gap: 4px;
}
.row1 { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; cursor: pointer; user-select: none; }
.row2 { display: flex; gap: 10px; align-items: center; font-size: 12px; color: var(--text-tertiary); }
.id { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.orderId { font-size: 13px; color: var(--text-secondary); }
.typeTag { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: var(--bg-secondary); color: var(--text-secondary); }
.amount { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.expandHint { font-size: 11px; color: var(--text-tertiary); margin-left: auto; }
.time { font-size: 11px; color: var(--text-tertiary); }
.deadline { font-size: 11px; color: var(--warning); }

/* 标签 */
.tag { font-size: 11px; padding: 2px 8px; border-radius: 6px; font-weight: 500; }
.tag.warn { background: var(--warning-bg); color: var(--warning); }
.tag.ok { background: var(--success-bg); color: var(--success); }
.tag.err { background: var(--error-bg); color: var(--error); }
.tag.dim { background: var(--bg-secondary); color: var(--text-tertiary); }
.tag.info { background: var(--info-bg); color: var(--info); }
.tag.active { background: #ede7f6; color: #512da8; }
.tag.resp { font-size: 11px; }

/* 详情区 */
.detail { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border-light); display: grid; gap: 14px; }
.infoGrid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; font-size: 13px; color: var(--text-secondary); }

/* 时间线 */
.timeline { display: flex; flex-direction: column; gap: 6px; padding-left: 8px; }
.tlItem { font-size: 12px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; }
.tlDot { width: 8px; height: 8px; border-radius: 50%; background: var(--border-strong); flex-shrink: 0; }
.tlDot.done { background: var(--success); }
.tlDot.closed { background: var(--error); }

/* 证据 */
.evidenceGrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; }
.evidenceItem { border: 1px solid var(--border-light); border-radius: 8px; overflow: hidden; }
.evidenceImg { width: 100%; height: 120px; object-fit: cover; display: block; }
.evidenceVideo { width: 100%; height: 120px; object-fit: contain; background: black; }
.evidenceText { padding: 8px; font-size: 12px; color: var(--text-secondary); margin: 0; }

/* 仲裁结果 */
.arbSection { background: var(--bg-hover); padding: 10px; border-radius: 8px; }
.arbText { font-size: 13px; color: var(--text-primary); margin: 0; white-space: pre-wrap; }

/* 仲裁表单 */
.arbitrateBox { border: 1px solid var(--border-light); border-radius: 10px; padding: 14px; background: var(--bg-tertiary); }
.arbForm { display: grid; gap: 10px; }
.arbRow { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.arbRow label { font-size: 12px; min-width: 80px; color: var(--text-secondary); }
.sel, .inp, .textarea {
  border: 1px solid var(--border-default); border-radius: 6px; padding: 6px 10px;
  font-size: 13px; outline: none; background: var(--bg-primary); color: var(--text-primary);
}
.sel { min-width: 140px; }
.inp { flex: 1; min-width: 100px; }
.inp.short { max-width: 120px; flex: none; }
.textarea { width: 100%; resize: vertical; font-family: inherit; }
.arbPreview {
  background: var(--bg-primary); border: 1px solid var(--border-light); border-radius: 8px;
  padding: 10px 14px; font-size: 13px; display: grid; gap: 4px; color: var(--text-primary);
}
.arbActions { display: flex; justify-content: flex-end; }

/* 按钮 */
.btn {
  padding: 8px 16px; border: 1px solid var(--border-default); border-radius: 8px;
  background: var(--bg-primary); cursor: pointer; font-size: 13px; color: var(--text-secondary);
}
.btn.primary { background: var(--text-primary); color: var(--bg-primary); border-color: var(--text-primary); }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 通用 */
.muted { color: var(--text-tertiary); font-size: 13px; }
.error { color: var(--error); font-size: 13px; }
</style>

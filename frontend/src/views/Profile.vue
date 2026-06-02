<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'

const router = useRouter()
const profile = ref({ nickname: '', phone: '', email: '', avatarUrl: '' })
const addresses = ref([])
const editMode = ref(false)
const form = ref({ nickname: '', phone: '', email: '' })
const msg = ref('')
const loading = ref(true)

const soldOrders = ref([])
const boughtOrders = ref([])
const showSold = ref(false)
const showBought = ref(false)

const favoritedProducts = ref([])
const showFavorites = ref(false)

const orderStatusLabel = {
  WAIT_PAY: '待支付', WAIT_DELIVER: '待发货', WAIT_RECEIVE: '待收货',
  COMPLETED: '已完成', CANCELLED: '已取消', AFTER_SALE: '售后中',
}

async function load() {
  try {
    const [p, addrs] = await Promise.all([
      api('/api/users/profile'),
      api('/api/users/addresses'),
    ])
    profile.value = p
    addresses.value = addrs || []
    form.value = { nickname: p.nickname, phone: p.phone || '', email: p.email || '' }
  } catch (e) {
    msg.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadSold() {
  showSold.value = !showSold.value
  if (!showSold.value || soldOrders.value.length) return
  try {
    const page = await api('/api/orders/sold?size=50&status=COMPLETED')
    soldOrders.value = page.content || []
    // 并行获取商品标题
    const ids = [...new Set(soldOrders.value.map(o => o.productId))]
    const titles = {}
    await Promise.all(ids.map(async id => {
      try { const prod = await api(`/api/products/${id}`, { auth: false }); titles[id] = prod.title }
      catch { titles[id] = `商品 #${id}` }
    }))
    // 把标题注入到 order 对象上
    soldOrders.value.forEach(o => o._title = titles[o.productId] || `商品 #${o.productId}`)
  } catch (e) { /* ignore */ }
}

async function loadFavorites() {
  showFavorites.value = !showFavorites.value
  if (!showFavorites.value || favoritedProducts.value.length) return
  try {
    const page = await api('/api/users/favorites?size=50')
    const favs = page.content || []
    const ids = [...new Set(favs.map(f => f.productId))]
    const products = {}
    await Promise.all(ids.map(async id => {
      try { const prod = await api(`/api/products/${id}`, { auth: false }); products[id] = prod }
      catch { products[id] = { title: `商品 #${id}`, priceCent: 0, coverImageUrl: '' } }
    }))
    favoritedProducts.value = favs.map(f => ({ ...f, _product: products[f.productId] }))
  } catch (e) { /* ignore */ }
}

async function loadBought() {
  showBought.value = !showBought.value
  if (!showBought.value || boughtOrders.value.length) return
  try {
    const page = await api('/api/orders/bought?size=50&status=COMPLETED')
    boughtOrders.value = page.content || []
    const ids = [...new Set(boughtOrders.value.map(o => o.productId))]
    const titles = {}
    await Promise.all(ids.map(async id => {
      try { const prod = await api(`/api/products/${id}`, { auth: false }); titles[id] = prod.title }
      catch { titles[id] = `商品 #${id}` }
    }))
    boughtOrders.value.forEach(o => o._title = titles[o.productId] || `商品 #${o.productId}`)
  } catch (e) { /* ignore */ }
}

async function saveProfile() {
  msg.value = ''
  try {
    const updated = await api('/api/users/profile', {
      method: 'PUT', body: form.value,
    })
    profile.value = updated
    editMode.value = false
    msg.value = '保存成功'
  } catch (e) {
    msg.value = e.message
  }
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('nickname')
  localStorage.removeItem('role')
  router.push('/login')
}

async function uploadAvatar(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  try {
    const url = await api('/api/users/avatar', {
      method: 'PUT', body: fd, raw: true,
    })
    profile.value.avatarUrl = url
  } catch (err) {
    msg.value = err.message
  }
}

async function setDefault(addrId) {
  await api(`/api/users/addresses/${addrId}/default`, { method: 'PUT' })
  await load()
}

async function deleteAddr(addrId) {
  if (!confirm('确定删除此地址？')) return
  await api(`/api/users/addresses/${addrId}`, { method: 'DELETE' })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h2>个人中心</h2>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-if="msg" :class="['msg', msg === '保存成功' ? 'ok' : 'err']">{{ msg }}</p>

    <!-- 资料卡片 -->
    <div class="card" v-if="!loading">
      <div class="avatarRow">
        <div class="avatar" :style="profile.avatarUrl ? { backgroundImage: `url(${profile.avatarUrl})` } : {}">
          <span v-if="!profile.avatarUrl">{{ (profile.nickname || '?')[0] }}</span>
        </div>
        <label class="uploadBtn">
          更换头像
          <input type="file" accept="image/*" hidden @change="uploadAvatar" />
        </label>
      </div>

      <template v-if="editMode">
        <label>昵称 <input v-model="form.nickname" /></label>
        <label>手机号 <input v-model="form.phone" placeholder="选填" /></label>
        <label>邮箱 <input v-model="form.email" placeholder="选填" /></label>
        <div class="row">
          <button class="btn primary" @click="saveProfile">保存</button>
          <button class="btn" @click="editMode = false">取消</button>
        </div>
      </template>
      <template v-else>
        <div class="info"><strong>{{ profile.nickname }}</strong></div>
        <div class="info muted">手机：{{ profile.phone || '未填写' }}</div>
        <div class="info muted">邮箱：{{ profile.email || '未填写' }}</div>
        <button class="btn" @click="editMode = true">编辑资料</button>
        <div class="bottomActions">
          <button class="btn" @click="router.push('/switch-account')">切换账号</button>
          <button class="btn danger" @click="logout">退出登录</button>
        </div>
      </template>
    </div>

    <!-- 已售出 -->
    <div class="card">
      <div class="head" @click="loadSold" style="cursor:pointer">
        <h3>已售出 <span class="arrow">{{ showSold ? '▾' : '▸' }}</span></h3>
      </div>
      <template v-if="showSold">
        <div v-if="!soldOrders.length" class="muted">暂无</div>
        <div v-else class="orderList">
          <div v-for="o in soldOrders" :key="o.id" class="orderItem" @click="router.push(`/orders/${o.id}`)">
            <div class="orderTop">
              <span class="orderTitle">{{ o._title }}</span>
              <span class="orderStatus">{{ orderStatusLabel[o.status] || o.status }}</span>
            </div>
            <div class="orderInfo">
              <span>¥{{ (o.amountCent / 100).toFixed(2) }}</span>
              <span class="orderTime">{{ o.createdAt?.substring(0, 16) }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 我的收藏 -->
    <div class="card">
      <div class="head" @click="loadFavorites" style="cursor:pointer">
        <h3>我的收藏 <span class="arrow">{{ showFavorites ? '▾' : '▸' }}</span></h3>
      </div>
      <template v-if="showFavorites">
        <div v-if="!favoritedProducts.length" class="muted">暂无收藏</div>
        <div v-else class="favList">
          <div v-for="fav in favoritedProducts" :key="fav.id"
            class="favItem" @click="router.push(`/products/${fav.productId}`)">
            <div class="favCover" :style="fav._product.coverImageUrl ? { backgroundImage: `url(${fav._product.coverImageUrl})` } : {}" />
            <div class="favInfo">
              <span class="favTitle">{{ fav._product.title }}</span>
              <span class="favPrice">¥{{ (fav._product.priceCent / 100).toFixed(2) }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 我的购买 -->
    <div class="card">
      <div class="head" @click="loadBought" style="cursor:pointer">
        <h3>我的购买 <span class="arrow">{{ showBought ? '▾' : '▸' }}</span></h3>
      </div>
      <template v-if="showBought">
        <div v-if="!boughtOrders.length" class="muted">暂无</div>
        <div v-else class="orderList">
          <div v-for="o in boughtOrders" :key="o.id" class="orderItem" @click="router.push(`/orders/${o.id}`)">
            <div class="orderTop">
              <span class="orderTitle">{{ o._title }}</span>
              <span class="orderStatus">{{ orderStatusLabel[o.status] || o.status }}</span>
            </div>
            <div class="orderInfo">
              <span>¥{{ (o.amountCent / 100).toFixed(2) }}</span>
              <span class="orderTime">{{ o.createdAt?.substring(0, 16) }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 地址管理 -->
    <div class="card">
      <div class="head">
        <h3>收货地址</h3>
        <button class="btn primary" @click="router.push('/profile/address/new')">新增地址</button>
      </div>
      <div v-if="!addresses.length" class="muted">暂无地址</div>
      <div v-else class="addrList">
        <div v-for="addr in addresses" :key="addr.id" class="addrItem">
          <div class="addrRow">
            <strong>{{ addr.receiverName }}</strong> {{ addr.receiverPhone }}
            <span v-if="addr.isDefault" class="tag">默认</span>
          </div>
          <div class="addrText">{{ addr.fullAddress }}</div>
          <div class="addrActions">
            <button class="link" @click="router.push(`/profile/address/${addr.id}/edit`)">编辑</button>
            <button class="link" v-if="!addr.isDefault" @click="setDefault(addr.id)">设为默认</button>
            <button class="link danger" @click="deleteAddr(addr.id)">删除</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 600px; margin: 0 auto; }
h2 { margin: 0 0 16px; font-size: 20px; }
h3 { margin: 0; font-size: 15px; }
.card {
  border: 1px solid rgba(0,0,0,0.08); border-radius: 16px;
  padding: 18px; margin-bottom: 14px; background: white;
}
.avatarRow { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.avatar {
  width: 56px; height: 56px; border-radius: 50%;
  background-color: #e0e0e0; background-position: center; background-size: cover; background-repeat: no-repeat;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; color: rgba(0,0,0,0.4);
}
.uploadBtn {
  font-size: 12px; color: rgba(0,0,0,0.5); cursor: pointer;
}
label {
  display: grid; gap: 4px; font-size: 13px; margin-bottom: 8px;
}
input {
  padding: 8px 10px; border: 1px solid rgba(0,0,0,0.12); border-radius: 10px; outline: none;
}
.row { display: flex; gap: 8px; margin-top: 8px; }
.btn {
  padding: 7px 14px; border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  background: white; cursor: pointer; font-size: 13px;
}
.btn.primary { background: black; color: white; border-color: black; }
.btn.danger { color: #b00020; border-color: #b00020; }
.bottomActions { margin-top: 10px; display: flex; gap: 8px; }
.info { margin-bottom: 6px; font-size: 14px; }
.muted { color: rgba(0,0,0,0.45); font-size: 13px; }
.msg { padding: 8px 12px; border-radius: 10px; font-size: 13px; }
.msg.ok { background: #e8f5e9; color: #2e7d32; }
.msg.err { background: #fce4ec; color: #b00020; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.addrList { display: grid; gap: 8px; }
.addrItem {
  padding: 10px; border: 1px solid rgba(0,0,0,0.06); border-radius: 10px;
}
.addrRow { font-size: 14px; display: flex; gap: 8px; align-items: center; }
.tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: rgba(0,0,0,0.06); }
.addrText { font-size: 12px; color: rgba(0,0,0,0.55); margin-top: 3px; }
.addrActions { margin-top: 6px; display: flex; gap: 12px; }
.link { background: none; border: 0; color: rgba(0,0,0,0.55); cursor: pointer; font-size: 12px; padding: 0; }
.link.danger { color: #b00020; }
.arrow { font-size: 12px; color: rgba(0,0,0,0.35); }
.orderList { display: grid; gap: 6px; margin-top: 8px; }
.orderItem {
  padding: 8px 10px; border: 1px solid rgba(0,0,0,0.06); border-radius: 10px;
  cursor: pointer; transition: background 0.12s;
}
.orderItem:hover { background: rgba(0,0,0,0.02); }
.orderTop { display: flex; justify-content: space-between; align-items: center; }
.orderTitle { font-size: 13px; font-weight: 500; }
.orderStatus { font-size: 11px; color: rgba(0,0,0,0.45); }
.orderInfo { display: flex; justify-content: space-between; margin-top: 3px; font-size: 12px; }
.orderTime { color: rgba(0,0,0,0.35); }
.favList { display: grid; gap: 6px; margin-top: 8px; }
.favItem {
  display: flex; gap: 10px; align-items: center;
  padding: 8px 10px; border: 1px solid rgba(0,0,0,0.06); border-radius: 10px;
  cursor: pointer; transition: background 0.12s;
}
.favItem:hover { background: rgba(0,0,0,0.02); }
.favCover {
  width: 44px; height: 44px; border-radius: 8px; flex-shrink: 0;
  background-color: #f3f3f3; background-position: center; background-size: cover; background-repeat: no-repeat;
}
.favInfo { display: grid; gap: 2px; min-width: 0; }
.favTitle { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.favPrice { font-size: 13px; font-weight: 600; }
</style>

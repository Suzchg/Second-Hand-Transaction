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
const notif = ref({ unreadMessages: 0, pendingOffersReceived: 0, pendingOrdersBuyer: 0, pendingOrdersSeller: 0 })

async function load() {
  try {
    const [p, addrs, n] = await Promise.all([
      api('/api/users/profile'),
      api('/api/users/addresses'),
      api('/api/users/notifications'),
    ])
    profile.value = p
    addresses.value = addrs || []
    notif.value = n || {}
    form.value = { nickname: p.nickname, phone: p.phone || '', email: p.email || '' }
  } catch (e) {
    msg.value = e.message
  } finally {
    loading.value = false
  }
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
    localStorage.setItem('avatarUrl', url)
    window.dispatchEvent(new CustomEvent('avatar-updated'))
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

const pendingTotal = () =>
  (notif.value.pendingOrdersBuyer || 0) +
  (notif.value.pendingOrdersSeller || 0) +
  (notif.value.pendingOffersReceived || 0)

onMounted(load)
</script>

<template>
  <div class="profilePage">
    <h2 class="pageTitle"><AppIcon name="user" :size="20"/> 个人中心</h2>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-if="msg" :class="['statusMsg', msg === '保存成功' ? 'ok' : 'err']">{{ msg }}</p>

    <!-- 资料卡片 -->
    <div v-if="!loading" class="profileCard">
      <div class="avatarRow">
        <div class="avatar" :style="profile.avatarUrl ? { backgroundImage: `url(${profile.avatarUrl})` } : {}">
          <span v-if="!profile.avatarUrl" class="avatarFallback">{{ (profile.nickname || '?')[0] }}</span>
        </div>
        <label class="uploadLabel">
          <AppIcon name="image" :size="14"/> 更换头像
          <input type="file" accept="image/*" hidden @change="uploadAvatar" />
        </label>
      </div>

      <template v-if="editMode">
        <div class="editGrid">
          <label>昵称 <input v-model="form.nickname" /></label>
          <label>手机号 <input v-model="form.phone" placeholder="选填" /></label>
          <label>邮箱 <input v-model="form.email" placeholder="选填" /></label>
        </div>
        <div class="btnRow">
          <button class="btnPrimary" @click="saveProfile">保存</button>
          <button class="btnGhost" @click="editMode = false">取消</button>
        </div>
      </template>
      <template v-else>
        <div class="infoGrid">
          <div class="infoItem">
            <span class="infoLabel">昵称</span>
            <span class="infoValue">{{ profile.nickname }}</span>
          </div>
          <div class="infoItem">
            <span class="infoLabel">手机</span>
            <span class="infoValue muted">{{ profile.phone || '未填写' }}</span>
          </div>
          <div class="infoItem">
            <span class="infoLabel">邮箱</span>
            <span class="infoValue muted">{{ profile.email || '未填写' }}</span>
          </div>
        </div>
        <div class="btnRow">
          <button class="btnGhost" @click="editMode = true"><AppIcon name="edit" :size="14"/> 编辑资料</button>
        </div>
        <div class="btnRow" style="margin-top: var(--space-md); border-top: 1px solid var(--border-light); padding-top: var(--space-md);">
          <button class="btnGhost" @click="router.push('/switch-account')"><AppIcon name="refresh" :size="14"/> 切换账号</button>
          <button class="btnDanger" @click="logout"><AppIcon name="logout" :size="14"/> 退出登录</button>
        </div>
      </template>
    </div>

    <!-- 快捷入口 -->
    <div class="entryCard">
      <h3><AppIcon name="bar-chart" :size="16"/> 交易管理</h3>
      <div class="entryGrid">
        <button class="entryBtn" @click="router.push('/my-orders')">
          <span class="entryIcon"><AppIcon name="clipboard" :size="24"/></span>
          <span class="entryLabel">
            我的订单
            <span v-if="pendingTotal() > 0" class="notifBadge">{{ pendingTotal() }}</span>
          </span>
          <span class="entryHint">买到的 · 卖出的</span>
        </button>
        <button class="entryBtn" @click="router.push('/my-favorites')">
          <span class="entryIcon"><AppIcon name="heart" :size="24"/></span>
          <span class="entryLabel">我的收藏</span>
          <span class="entryHint">收藏的商品</span>
        </button>
        <button class="entryBtn" @click="router.push('/my-products')">
          <span class="entryIcon"><AppIcon name="package" :size="24"/></span>
          <span class="entryLabel">我在售的</span>
          <span class="entryHint">管理在售商品</span>
        </button>
      </div>
    </div>

    <!-- 地址管理 -->
    <div class="addressCard">
      <div class="cardHead">
        <h3><AppIcon name="map-pin" :size="16"/> 收货地址</h3>
        <button class="btnPrimary" @click="router.push('/profile/address/new')">+ 新增地址</button>
      </div>
      <div v-if="!addresses.length" class="emptyHint">暂无地址，添加一个吧</div>
      <div v-else class="addressList">
        <div v-for="addr in addresses" :key="addr.id" class="addressItem">
          <div class="addrMain">
            <div class="addrName">
              <strong>{{ addr.receiverName }}</strong>
              <span>{{ addr.receiverPhone }}</span>
              <span v-if="addr.isDefault" class="defaultTag">默认</span>
              <span v-else-if="addr.tag" class="labelTag">{{ addr.tag }}</span>
            </div>
            <div class="addrText">{{ addr.fullAddress }}</div>
          </div>
          <div class="addrActions">
            <button class="addrLink" @click="router.push(`/profile/address/${addr.id}/edit`)">编辑</button>
            <button v-if="!addr.isDefault" class="addrLink" @click="setDefault(addr.id)">设为默认</button>
            <button class="addrLink danger" @click="deleteAddr(addr.id)">删除</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profilePage { max-width: 620px; margin: 0 auto; }

.pageTitle { font-size: 20px; font-weight: 700; margin: 0 0 var(--space-xl); }

.muted { color: var(--text-tertiary); font-size: 13px; }

.statusMsg {
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: 13px;
  margin-bottom: var(--space-md);
}

.statusMsg.ok { background: var(--success-bg); color: var(--success); border: 1px solid var(--success-border); }
.statusMsg.err { background: var(--error-bg); color: var(--error); border: 1px solid var(--error-border); }

/* 卡片通用 */
.profileCard, .entryCard, .addressCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-xl);
  margin-bottom: var(--space-lg);
  box-shadow: var(--shadow-xs);
}

h3 { margin: 0; font-size: 15px; font-weight: 600; }

/* 资料卡片 */
.avatarRow {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatarFallback {
  font-size: 28px;
  color: var(--text-tertiary);
}

.uploadLabel {
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color var(--transition-fast);
}

.uploadLabel:hover { color: var(--brand-darker); }

.editGrid {
  display: grid;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.editGrid label {
  display: grid;
  gap: var(--space-xs);
  font-size: 13px;
  color: var(--text-secondary);
}

.editGrid input {
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 14px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.infoGrid { margin-bottom: var(--space-md); }

.infoItem {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-sm) 0;
}

.infoLabel { font-size: 13px; color: var(--text-tertiary); }

.infoValue { font-size: 14px; font-weight: 500; }

.btnRow { display: flex; gap: var(--space-sm); }

.btnPrimary {
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 9px 20px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
}

.btnPrimary:hover { opacity: 0.9; }

.btnGhost {
  border: 1.5px solid var(--border-default);
  background: var(--bg-primary);
  padding: 9px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}

.btnGhost:hover { border-color: var(--border-strong); }

.btnDanger {
  border: 1.5px solid var(--error-border);
  background: var(--bg-primary);
  padding: 9px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--error);
}

.btnDanger:hover { background: var(--error-bg); }

/* 快捷入口 */
.entryGrid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-md); margin-top: var(--space-md); }

.entryBtn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-lg) var(--space-sm);
  border: 1.5px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.entryBtn:hover {
  border-color: var(--brand);
  background: var(--brand-light);
  transform: translateY(-2px);
  box-shadow: var(--shadow-brand);
}

.entryIcon { display: flex; align-items: center; justify-content: center; }

.entryLabel {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.entryHint { font-size: 11px; color: var(--text-tertiary); }

.notifBadge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--radius-full);
  background: var(--error);
  color: white;
  font-size: 11px;
  font-weight: 700;
}

/* 地址管理 */
.cardHead {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
}

.emptyHint { color: var(--text-tertiary); font-size: 13px; text-align: center; padding: var(--space-xl); }

.addressList { display: grid; gap: var(--space-sm); }

.addressItem {
  padding: var(--space-md);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
}

.addressItem:hover { background: var(--bg-tertiary); }

.addrMain { display: grid; gap: 4px; }

.addrName {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
}

.defaultTag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--brand-light);
  color: var(--brand-darker);
  font-weight: 600;
}

.labelTag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--info-bg);
  color: var(--info);
}

.addrText { font-size: 12px; color: var(--text-tertiary); margin-top: 2px; }

.addrActions {
  margin-top: var(--space-sm);
  display: flex;
  gap: var(--space-md);
}

.addrLink {
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 12px;
  padding: 0;
  transition: color var(--transition-fast);
}

.addrLink:hover { color: var(--text-primary); }

.addrLink.danger:hover { color: var(--error); }

@media (max-width: 480px) {
  .entryGrid { grid-template-columns: repeat(3, 1fr); }
  .entryBtn { padding: var(--space-md) var(--space-xs); }
  .entryIcon { font-size: 20px; }
  .entryLabel { font-size: 12px; }
}
</style>

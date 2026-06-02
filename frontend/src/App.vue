<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from './api.js'

const router = useRouter()

// 同步读取 localStorage，确保页面加载时立即显示正确状态
const nickname = ref(localStorage.getItem('nickname') || '')
const isLoggedIn = ref(!!localStorage.getItem('token'))
const isAdmin = ref(localStorage.getItem('role') === 'ADMIN')

async function fetchUser() {
  if (!localStorage.getItem('token')) return
  try {
    const user = await api('/api/auth/me')
    nickname.value = user.nickname || '用户'
    isLoggedIn.value = true
    isAdmin.value = user.role === 'ADMIN'
    localStorage.setItem('nickname', user.nickname || '用户')
    localStorage.setItem('role', user.role || 'USER')
    localStorage.setItem('userId', String(user.userId || ''))
    // 保存账号到记忆列表
    saveAccount({
      userId: user.userId,
      nickname: user.nickname || '用户',
      role: user.role || 'USER',
      token: localStorage.getItem('token'),
      identifier: localStorage.getItem('lastIdentifier') || '',
    })
  } catch {
    // token 失效（过期、数据库重建等），清除登录态
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('role')
    isLoggedIn.value = false
    isAdmin.value = false
    nickname.value = ''
    // 将保存的账号 token 也清除
    try {
      const list = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
      list.forEach(a => { a.token = '' })
      localStorage.setItem('savedAccounts', JSON.stringify(list))
    } catch { /* ignore */ }
  }
}

function saveAccount(acc) {
  try {
    const list = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
    const idx = list.findIndex(a => a.userId === acc.userId)
    if (idx >= 0) {
      // 更新但不覆盖首次保存的 identifier（避免被其他账号的 lastIdentifier 污染）
      acc.identifier = list[idx].identifier || acc.identifier
      list[idx] = acc
    } else {
      list.push(acc)
    }
    localStorage.setItem('savedAccounts', JSON.stringify(list))
  } catch { /* ignore */ }
}

onMounted(() => {
  if (isLoggedIn.value) fetchUser()
})
</script>

<template>
  <div class="appShell">
    <header class="topBar">
      <div class="brand" @click="router.push('/')">SecondHand</div>
      <nav class="nav">
        <button class="ghost" @click="router.push('/')">商品</button>
        <template v-if="isLoggedIn">
          <button class="ghost" @click="router.push('/sell')">发布</button>
          <button class="ghost" @click="router.push('/my-products')">在售</button>
          <button class="ghost" @click="router.push('/messages')">消息</button>
          <button class="ghost" @click="router.push('/my-orders')">订单</button>
          <button v-if="isAdmin" class="ghost adminBtn" @click="router.push('/admin')">管理</button>
          <button class="ghost" @click="router.push('/profile')">{{ nickname }}</button>
          <button class="ghost" @click="router.push('/switch-account')">切换</button>
        </template>
        <template v-else>
          <button class="ghost primaryGhost" @click="router.push('/login')">登录</button>
        </template>
      </nav>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.appShell { min-height: 100vh; }
.topBar {
  position: sticky; top: 0; z-index: 10;
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid rgba(0,0,0,0.08);
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(12px);
}
.brand { font-weight: 700; font-size: 16px; cursor: pointer; user-select: none; }
.nav { display: flex; align-items: center; gap: 8px; }
.ghost {
  border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 6px 12px; border-radius: 10px; cursor: pointer; font-size: 13px;
  transition: border-color 0.15s;
}
.ghost:hover { border-color: rgba(0,0,0,0.25); }
.primaryGhost { background: black; color: white; border-color: black; }
.adminBtn { border-color: #e65100; color: #e65100; }
.main { max-width: 1040px; margin: 0 auto; padding: 20px; }
</style>

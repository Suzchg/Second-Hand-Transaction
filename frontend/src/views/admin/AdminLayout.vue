<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menu = [
  { path: '/admin/dashboard', label: '📊 数据总览' },
  { path: '/admin/users', label: '👥 用户管理' },
  { path: '/admin/products', label: '📦 商品管理' },
  { path: '/admin/orders', label: '📋 订单管理' },
  { path: '/admin/reports', label: '🚨 举报管理' },
]

function isActive(p) { return route.path === p }

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('nickname')
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <header class="top">
      <span class="logo" @click="router.push('/')">SecondHand Admin</span>
      <div class="right">
        <button class="btn" @click="router.push('/')">← 返回前台</button>
        <button class="btn" @click="logout">退出</button>
      </div>
    </header>
    <div class="body">
      <aside class="side">
        <div
          v-for="m in menu" :key="m.path"
          :class="['menuItem', { active: isActive(m.path) }]"
          @click="router.push(m.path)"
        >{{ m.label }}</div>
      </aside>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell { min-height: 100vh; display: flex; flex-direction: column; }
.top {
  height: 48px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px; border-bottom: 1px solid rgba(0,0,0,0.08);
  background: rgba(255,255,255,0.95);
  position: sticky; top: 0; z-index: 10;
}
.logo { font-weight: 700; font-size: 15px; cursor: pointer; }
.right { display: flex; gap: 8px; }
.btn {
  border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 5px 12px; border-radius: 8px; cursor: pointer; font-size: 12px;
}
.body { display: flex; flex: 1; }
.side {
  width: 180px; flex-shrink: 0;
  border-right: 1px solid rgba(0,0,0,0.06);
  padding: 12px 0;
}
.menuItem {
  padding: 10px 20px; cursor: pointer; font-size: 14px;
  color: rgba(0,0,0,0.65); transition: all 0.12s;
}
.menuItem:hover { background: rgba(0,0,0,0.03); color: rgba(0,0,0,0.85); }
.menuItem.active { background: rgba(0,0,0,0.06); color: rgba(0,0,0,0.9); font-weight: 600; }
.content { flex: 1; padding: 20px; min-width: 0; }
</style>

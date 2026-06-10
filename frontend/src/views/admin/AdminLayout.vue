<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menu = [
  { path: '/admin/dashboard', icon: 'bar-chart', title: '数据总览' },
  { path: '/admin/users', icon: 'users', title: '用户管理' },
  { path: '/admin/products', icon: 'package', title: '商品管理' },
  { path: '/admin/orders', icon: 'clipboard', title: '订单管理' },
  { path: '/admin/reports', icon: 'alert-octagon', title: '举报管理' },
  { path: '/admin/after-sale', icon: 'tool', title: '售后管理' },
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
  <div class="adminShell">
    <!-- 顶栏 -->
    <header class="adminTop">
      <div class="topLeft">
        <span class="adminLogo" @click="router.push('/')"><AppIcon name="cart" :size="16"/> Second hand</span>
        <span class="adminBadge">管理后台</span>
      </div>
      <div class="topRight">
        <button class="topBtn" @click="router.push('/')">← 返回前台</button>
        <button class="topBtn danger" @click="logout">退出</button>
      </div>
    </header>

    <div class="adminBody">
      <!-- 侧边栏 -->
      <aside class="adminSide">
        <nav class="sideNav">
          <div
            v-for="m in menu" :key="m.path"
            :class="['sideItem', { active: isActive(m.path) }]"
            @click="router.push(m.path)"
          >
            <span class="sideIcon"><AppIcon :name="m.icon" :size="16"/></span>
            <span class="sideTitle">{{ m.title }}</span>
            <span v-if="isActive(m.path)" class="sideIndicator" />
          </div>
        </nav>
      </aside>

      <!-- 内容区 -->
      <main class="adminContent">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.adminShell {
  min-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.adminTop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 var(--space-xl);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-light);
  position: sticky;
  top: 56px;
  z-index: 90;
}

.topLeft { display: flex; align-items: center; gap: var(--space-md); }

.adminLogo {
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  color: var(--text-primary);
}

.adminBadge {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--brand-light);
  color: var(--brand-darker);
  font-weight: 600;
}

.topRight { display: flex; gap: var(--space-sm); }

.topBtn {
  border: 1px solid var(--border-default);
  background: var(--bg-primary);
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.topBtn:hover { border-color: var(--border-strong); color: var(--text-primary); }

.topBtn.danger { color: var(--error); border-color: var(--error-border); }
.topBtn.danger:hover { background: var(--error-bg); }

/* 主体布局 */
.adminBody {
  display: flex;
  flex: 1;
}

/* 侧边栏 */
.adminSide {
  width: 200px;
  flex-shrink: 0;
  background: var(--bg-primary);
  border-right: 1px solid var(--border-light);
  padding: var(--space-md) 0;
}

.sideNav {
  display: grid;
  gap: 2px;
  padding: 0 var(--space-sm);
}

.sideItem {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
  position: relative;
  overflow: hidden;
}

.sideItem:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.sideItem.active {
  background: var(--brand-light);
  color: var(--brand-darker);
  font-weight: 600;
}

.sideIcon { display: flex; align-items: center; flex-shrink: 0; }

.sideTitle { flex: 1; }

.sideIndicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  border-radius: 0 2px 2px 0;
  background: var(--brand-dark);
}

/* 内容区 */
.adminContent {
  flex: 1;
  min-width: 0;
  padding: var(--space-xl);
  background: var(--bg-secondary);
}

/* 响应式 */
@media (max-width: 768px) {
  .adminTop { padding: 0 var(--space-md); }
  .adminSide { width: 160px; }
  .sideItem { padding: 8px 12px; font-size: 12px; }
  .adminContent { padding: var(--space-md); }
}

@media (max-width: 600px) {
  .adminBody { flex-direction: column; }
  .adminSide { width: 100%; border-right: none; border-bottom: 1px solid var(--border-light); padding: var(--space-sm); }
  .sideNav { display: flex; overflow-x: auto; }
  .sideItem { flex-shrink: 0; white-space: nowrap; }
  .sideIndicator { display: none; }
}
</style>

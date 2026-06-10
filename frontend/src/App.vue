<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { toast } from './toast.js'
import { useUserStore } from './stores/user.js'
import { useThemeStore } from './stores/theme.js'
import { useNotificationStore } from './stores/notification.js'
import Toast from './components/Toast.vue'

const router = useRouter()
const route = useRoute()
const toastRef = ref(null)
toast.setup((msg, type) => toastRef.value?.show(msg, type))

// ---- Pinia stores ----
const user = useUserStore()
const theme = useThemeStore()
const notif = useNotificationStore()

const searchKeyword = ref('')
const searchFocused = ref(false)

function doSearch() {
  const kw = searchKeyword.value.trim()
  if (kw) router.push({ path: '/', query: { keyword: kw } })
}

function clearSearch() {
  searchKeyword.value = ''
  router.push({ path: '/' })
}

// 登录页隐藏搜索栏
const hideSearch = computed(() => route.path === '/login')
// 右侧按钮：在当前页面对应的按钮隐藏
const showMsgBtn = computed(() => route.path !== '/messages')
const showSellBtn = computed(() => route.path !== '/sell')
const showProductsBtn = computed(() => route.path !== '/my-products')
const showOrdersBtn = computed(() => route.path !== '/my-orders')
const showAfterSalesBtn = computed(() => route.path !== '/my-after-sales')
const showAdminBtn = computed(() => !route.path.startsWith('/admin'))

// 外部事件：消息已读 / 操作完成 → 刷新通知
function onRefreshNotif() { notif.fetch() }

onMounted(() => {
  theme.init()
  window.addEventListener('msg-read', onRefreshNotif)
  window.addEventListener('action-done', onRefreshNotif)
  window.addEventListener('avatar-updated', () => {
    user.updateAvatar(localStorage.getItem('avatarUrl') || '')
  })
  if (user.isLoggedIn) {
    user.fetchProfile()
    notif.startPolling()
  }
})
onUnmounted(() => {
  window.removeEventListener('msg-read', onRefreshNotif)
  window.removeEventListener('action-done', onRefreshNotif)
  window.removeEventListener('avatar-updated', () => {})
  notif.stopPolling()
})
</script>

<template>
  <div class="appShell">
    <!-- 顶部导航栏 -->
    <header class="topBar">
      <!-- Logo -->
      <div class="logoArea" @click="router.push('/')">
        <AppIcon name="cart" :size="28" class="logoIcon"/>
        <span class="logoText">Second hand</span>
      </div>

      <!-- 搜索栏（居中自动填充，登录页隐藏） -->
      <div v-if="!hideSearch" class="searchWrapper">
        <div class="searchArea" :class="{ focused: searchFocused }">
          <svg class="searchIcon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            v-model="searchKeyword"
            class="searchInput"
            placeholder="搜一搜，发现好物..."
            @keyup.enter="doSearch"
            @focus="searchFocused = true"
            @blur="searchFocused = false"
          />
          <button
            v-if="searchKeyword"
            class="searchClear"
            @mousedown.prevent="clearSearch"
            title="清除搜索"
          ><AppIcon name="close" :size="12"/></button>
        </div>
      </div>

      <!-- 右侧用户区 -->
      <div class="userArea">
        <button class="iconOnly" :title="theme.isDark ? '浅色模式' : '深色模式'" @click="theme.toggle">
          <AppIcon :name="theme.isDark ? 'sun' : 'moon'" :size="20"/>
        </button>

        <template v-if="user.isLoggedIn">
          <button class="userBtn" @click="router.push('/profile')">
            <span
              class="userAvatar"
              :style="user.avatarUrl ? { backgroundImage: `url(${user.avatarUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : {}"
            >
              <span v-if="!user.avatarUrl">{{ user.nickname[0] }}</span>
            </span>
            <span class="userName">{{ user.nickname }}</span>
          </button>
        </template>
        <template v-else>
          <button class="loginBtn" @click="router.push('/login')">登录</button>
        </template>
      </div>
    </header>

    <!-- 主体：内容 + 右侧功能按钮 -->
    <div class="mainWrapper">
      <main class="mainArea">
        <router-view />
      </main>

      <!-- 右侧浮动功能按钮（当前页面对应按钮隐藏） -->
      <aside v-if="user.isLoggedIn" class="sideActions">
        <button v-if="showMsgBtn" class="sideBtn" @click="router.push('/messages')" title="消息">
          <span class="sideBtnIcon"><AppIcon name="chat" :size="22"/></span>
          <span class="sideBtnLabel">消息</span>
          <span v-if="notif.unreadMessages > 0" class="sideBadge">{{ notif.unreadMessages > 99 ? '99+' : notif.unreadMessages }}</span>
        </button>
        <button v-if="showSellBtn" class="sideBtn publishBtn" @click="router.push('/sell')" title="发布">
          <span class="sideBtnIcon plusIcon"><AppIcon name="plus" :size="24"/></span>
          <span class="sideBtnLabel">发布</span>
        </button>
        <button v-if="showProductsBtn" class="sideBtn" @click="router.push('/my-products')" title="在售">
          <span class="sideBtnIcon"><AppIcon name="package" :size="22"/></span>
          <span class="sideBtnLabel">在售</span>
        </button>
        <button v-if="showOrdersBtn" class="sideBtn" @click="router.push('/my-orders')" title="订单">
          <span class="sideBtnIcon"><AppIcon name="clipboard" :size="22"/></span>
          <span class="sideBtnLabel">订单</span>
          <span v-if="notif.pendingTotal > 0" class="sideBadge">{{ notif.pendingTotal > 99 ? '99+' : notif.pendingTotal }}</span>
        </button>
        <button v-if="showAfterSalesBtn" class="sideBtn" @click="router.push('/my-after-sales')" title="售后">
          <span class="sideBtnIcon"><AppIcon name="tool" :size="22"/></span>
          <span class="sideBtnLabel">售后</span>
        </button>
        <button v-if="user.isAdmin && showAdminBtn" class="sideBtn adminSideBtn" @click="router.push('/admin')" title="管理">
          <span class="sideBtnIcon"><AppIcon name="settings" :size="22"/></span>
          <span class="sideBtnLabel">管理</span>
        </button>
      </aside>
    </div>

    <!-- Footer（桌面端） -->
    <footer class="footer desktopOnly">
      <span><AppIcon name="cart" :size="14"/> Second hand © 2026</span>
      <span class="footerLinks">
        <router-link to="/policy/after-sale">售后说明</router-link>
        <span class="footerSep">·</span>
        <router-link to="/policy/privacy">隐私政策</router-link>
      </span>
    </footer>

    <!-- 移动端底部导航栏 -->
    <nav v-if="user.isLoggedIn" class="bottomNav">
      <button
        :class="['bottomNavBtn', { active: route.path === '/' }]"
        @click="router.push('/')"
      >
        <span class="bnIcon"><AppIcon name="home" :size="22"/></span>
        <span class="bnLabel">首页</span>
      </button>
      <button
        :class="['bottomNavBtn', { active: route.path === '/sell' }]"
        @click="router.push('/sell')"
      >
        <span class="bnIcon" style="color:var(--brand)"><AppIcon name="plus" :size="24"/></span>
        <span class="bnLabel">发布</span>
      </button>
      <button
        :class="['bottomNavBtn', { active: route.path === '/messages' }]"
        @click="router.push('/messages')"
      >
        <span class="bnIconWrap">
          <span class="bnIcon"><AppIcon name="chat" :size="22"/></span>
          <span v-if="notif.unreadMessages > 0" class="bnBadge">{{ notif.unreadMessages > 99 ? '99+' : notif.unreadMessages }}</span>
        </span>
        <span class="bnLabel">消息</span>
      </button>
      <button
        :class="['bottomNavBtn', { active: route.path === '/my-orders' }]"
        @click="router.push('/my-orders')"
      >
        <span class="bnIconWrap">
          <span class="bnIcon"><AppIcon name="clipboard" :size="22"/></span>
          <span v-if="notif.pendingTotal > 0" class="bnBadge">{{ notif.pendingTotal > 99 ? '99+' : notif.pendingTotal }}</span>
        </span>
        <span class="bnLabel">订单</span>
      </button>
      <button
        :class="['bottomNavBtn', { active: route.path === '/profile' }]"
        @click="router.push('/profile')"
      >
        <span
          class="bnAvatar"
          :style="user.avatarUrl ? { backgroundImage: `url(${user.avatarUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : {}"
        >
          <span v-if="!user.avatarUrl">{{ user.nickname[0] }}</span>
        </span>
        <span class="bnLabel">我的</span>
      </button>
    </nav>

    <Toast ref="toastRef" />
  </div>
</template>

<style>
/* ---- 全局布局 ---- */
.appShell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---- 顶栏 ---- */
.topBar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  padding: 0 24px;
  height: 60px;
  gap: 20px;
}

/* Logo */
.logoArea {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.logoIcon { display: flex; align-items: center; }

.logoText {
  font-size: 24px;
  font-weight: 800;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 3px;
  white-space: nowrap;
  text-transform: lowercase;
  font-style: italic;
}

/* 搜索栏（中间填充） */
.searchWrapper {
  flex: 1;
  display: flex;
  justify-content: center;
}

.searchArea {
  width: 100%;
  max-width: 560px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--bg-secondary);
  border: 1.5px solid transparent;
  border-radius: var(--radius-full);
  padding: 10px 20px;
  transition: all var(--transition-fast);
}

.searchArea:focus-within {
  background: var(--bg-primary);
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(14, 181, 166, 0.12);
}

.searchIcon { width: 18px; height: 18px; flex-shrink: 0; opacity: 0.4; color: var(--text-secondary); }

.searchInput {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--text-primary);
  outline: none;
  padding: 0;
}

.searchInput::placeholder { color: var(--text-tertiary); }

.searchClear {
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: #c0c0c0;
  color: white;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
  line-height: 1;
  transition: background var(--transition-fast);
}

.searchClear:hover { background: #999; }

/* 用户区 */
.userArea {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.iconOnly {
  width: 38px;
  height: 38px;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast);
}

.iconOnly:hover { background: var(--bg-secondary); }

.userBtn {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border-light);
  background: var(--bg-secondary);
  padding: 5px 14px 5px 5px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.userBtn:hover {
  border-color: var(--brand);
  background: var(--brand-light);
}

.userAvatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
}

.userName {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loginBtn {
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 9px 22px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  box-shadow: var(--shadow-brand);
  transition: all var(--transition-fast);
}

.loginBtn:hover { opacity: 0.9; transform: translateY(-1px); }

/* ---- 主内容区 + 右侧按钮 ---- */
.mainWrapper {
  flex: 1;
  display: flex;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 20px;
}

.mainArea {
  flex: 1;
  min-width: 0;
}

/* ---- 右侧浮动功能按钮（固定垂直居中）---- */
.sideActions {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  right: 16px;
  width: 56px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 90;
}

.sideBtn {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 56px;
  padding: 14px 4px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-primary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.sideBtn:hover {
  border-color: var(--brand);
  background: var(--brand-light);
  transform: translateX(-2px);
  box-shadow: var(--shadow-brand);
}

.sideBtnIcon { line-height: 1; display: flex; align-items: center; justify-content: center; }

.plusIcon { color: var(--brand); }

.publishBtn {
  border-color: var(--brand);
  background: var(--brand-light);
}

.publishBtn:hover {
  background: var(--brand);
}

.publishBtn:hover .plusIcon { color: white; }
.publishBtn:hover .sideBtnLabel { color: white; }

.sideBtnLabel {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-secondary);
}

.sideBadge {
  position: absolute;
  top: 4px;
  right: 6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--radius-full);
  background: var(--error);
  color: white;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(255, 77, 79, 0.3);
}

.adminSideBtn { border-color: var(--border-strong); margin-top: 12px; }

/* ---- Footer ---- */
.footer {
  border-top: 1px solid var(--border-light);
  background: var(--bg-primary);
  padding: 14px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

.footerLinks {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footerLinks a {
  color: var(--text-tertiary);
  text-decoration: none;
}

.footerLinks a:hover { color: var(--text-secondary); }

.footerSep { color: var(--border-strong); }

/* ---- 移动端底部导航栏 ---- */
.bottomNav {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--bg-primary);
  border-top: 1px solid var(--border-light);
  padding: 6px 0 env(safe-area-inset-bottom, 8px);
  justify-content: space-around;
  align-items: center;
}

.bottomNavBtn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 4px;
  color: var(--text-tertiary);
  transition: color var(--transition-fast);
  -webkit-tap-highlight-color: transparent;
}

.bottomNavBtn.active { color: var(--brand); }

.bnIcon { line-height: 1; display: flex; align-items: center; justify-content: center; }

.bnIconWrap {
  position: relative;
  display: inline-flex;
  line-height: 1;
}

.bnBadge {
  position: absolute;
  top: -6px;
  right: -10px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--radius-full);
  background: var(--error);
  color: white;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}

.bnLabel { font-size: 10px; font-weight: 500; }

.bnAvatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  overflow: hidden;
}

/* ---- 桌面端专用 ---- */
.desktopOnly { display: flex; }

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  /* 顶栏缩小 */
  .topBar { padding: 0 12px; gap: 10px; height: 50px; }
  .logoText { font-size: 18px; letter-spacing: 1.5px; }
  .logoIcon { /* SVG handles sizing */ }
  .searchArea { padding: 8px 14px; }
  .searchInput { font-size: 14px; }
  .searchIcon { width: 16px; height: 16px; }

  /* 主内容区增加底部间距（为底部导航留空） */
  .mainWrapper { padding: 12px 12px 80px 12px; }

  /* 隐藏桌面端右侧按钮和footer */
  .sideActions { display: none !important; }
  .desktopOnly { display: none !important; }

  /* 显示底部导航 */
  .bottomNav { display: flex; }

  /* 用户按钮简化 */
  .userName { display: none; }
  .userBtn { padding: 4px 8px 4px 4px; }
  .userAvatar { width: 28px; height: 28px; font-size: 12px; }
  .iconOnly { width: 34px; height: 34px; font-size: 16px; }
}

@media (max-width: 480px) {
  .topBar { padding: 0 10px; gap: 8px; height: 46px; }
  .logoText { display: none; }
  .logoIcon { /* SVG handles sizing */ }
  .searchArea { padding: 6px 12px; }
  .searchInput { font-size: 13px; }
  .mainWrapper { padding: 8px 8px 76px 8px; }
  .userBtn { padding: 3px; }
}
</style>

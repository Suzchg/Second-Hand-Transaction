<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { api } from '../api.js'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// ---- Tabs ----
const activeTab = ref('chat')

// ---- Chat state ----
const conversations = ref([])
const loading = ref(true)
const error = ref('')
const activeProductId = ref(null)
const activeOther = ref(null)
const messages = ref([])
const text = ref('')
const sending = ref(false)
const chatError = ref('')
const chatBody = ref(null)

// ---- System messages state ----
const systemMessages = ref([])
const sysLoading = ref(false)

// ---- Comment notifications state ----
const commentNotifications = ref([])
const commentLoading = ref(false)

const currentUserId = Number(localStorage.getItem('userId') || '0')

let timer = null

// ---- Conversation list ----
async function loadConversations() {
  try {
    const list = await api('/api/users/messages') || []
    for (const c of conversations.value) {
      if (c._virtual && !list.find(x => x.productId === c.productId && x.otherUserId === c.otherUserId)) {
        list.unshift(c)
      }
    }
    conversations.value = list
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

// ---- Chat ----
async function loadMessages() {
  if (!activeProductId.value || !activeOther.value) return
  try {
    messages.value = await api(`/api/products/${activeProductId.value}/chat?with=${activeOther.value}`) || []
  } catch { /* ignore */ }
}

async function sendMessage() {
  if (!text.value.trim()) return
  sending.value = true; chatError.value = ''
  try {
    await api(`/api/products/${activeProductId.value}/chat`, {
      method: 'POST',
      body: { receiverId: activeOther.value, content: text.value.trim() },
    })
    text.value = ''
    await loadMessages()
    scrollBottom()
    await loadConversations()
  } catch (e) { chatError.value = e.message }
  finally { sending.value = false }
}

async function openChat(conv) {
  activeProductId.value = conv.productId
  activeOther.value = conv.otherUserId
  await loadMessages()
  scrollBottom()
  if (!conv._virtual) {
    try {
      await api('/api/messages/read', {
        method: 'PUT',
        body: { productId: conv.productId, otherUserId: conv.otherUserId },
      })
      await loadConversations()
      window.dispatchEvent(new CustomEvent('msg-read'))
    } catch { /* ignore */ }
  }
}

function scrollBottom() {
  nextTick(() => {
    const el = chatBody.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function startNewConversation(productId) {
  try {
    const product = await api(`/api/products/${productId}`, { auth: false })
    if (!product || !product.sellerId) {
      error.value = '商品不存在'
      return
    }
    if (product.sellerId === currentUserId) {
      error.value = '不能和自己聊天'
      return
    }
    const virtualConv = {
      productId: product.id,
      productTitle: product.title,
      otherUserId: product.sellerId,
      otherUserName: '卖家',
      otherUserAvatar: null,
      lastMessage: '',
      lastMessageTime: null,
      unreadCount: 0,
      _virtual: true,
    }
    conversations.value.unshift(virtualConv)
    openChat(virtualConv)
  } catch (e) {
    error.value = e.message || '加载商品信息失败'
  }
}

watch(() => route.query.product, async (pid) => {
  if (pid) {
    const conv = conversations.value.find(c => c.productId === Number(pid))
    if (conv) {
      openChat(conv)
    } else {
      await startNewConversation(Number(pid))
    }
  }
})

function displayName(conv) {
  return conv.otherUserName || '未知用户'
}

const activeConv = computed(() =>
  conversations.value.find(c => c.productId === activeProductId.value && c.otherUserId === activeOther.value)
)

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// ---- System messages ----
async function loadSystemMessages() {
  sysLoading.value = true
  try {
    systemMessages.value = await api('/api/messages/system') || []
  } catch (e) {
    error.value = e.message
  } finally {
    sysLoading.value = false
  }
}

// ---- Comment notifications ----
async function loadCommentNotifications() {
  commentLoading.value = true
  try {
    commentNotifications.value = await api('/api/messages/comments') || []
  } catch (e) {
    error.value = e.message
  } finally {
    commentLoading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'system') loadSystemMessages()
  if (tab === 'comments') loadCommentNotifications()
}

onMounted(async () => {
  await loadConversations()
  const pid = route.query.product
  if (pid) {
    const conv = conversations.value.find(c => c.productId === Number(pid))
    if (conv) {
      openChat(conv)
    } else {
      await startNewConversation(Number(pid))
    }
  }
  timer = setInterval(async () => {
    await loadConversations()
    if (activeProductId.value) await loadMessages()
  }, 3000)
})
onUnmounted(() => { clearInterval(timer) })
</script>

<template>
  <div class="msgPage" :class="{ hasChat: activeTab === 'chat' && activeProductId && activeOther }">
    <!-- 左侧面板 -->
    <div class="leftPanel">
      <h2 class="panelTitle">消息中心</h2>

      <!-- Tabs -->
      <div class="msgTabs">
        <button :class="['msgTab', { active: activeTab === 'chat' }]" @click="switchTab('chat')">
          <AppIcon name="chat" :size="14"/> 对话
        </button>
        <button :class="['msgTab', { active: activeTab === 'system' }]" @click="switchTab('system')">
          <AppIcon name="package" :size="14"/> 系统
        </button>
        <button :class="['msgTab', { active: activeTab === 'comments' }]" @click="switchTab('comments')">
          <AppIcon name="edit" :size="14"/> 评论
        </button>
      </div>

      <!-- 对话列表 -->
      <template v-if="activeTab === 'chat'">
        <p v-if="loading" class="muted">加载中...</p>
        <p v-else-if="error" class="error">{{ error }}</p>
        <div v-else-if="!conversations.length" class="emptyList">
          <span class="emptyIcon"><AppIcon name="chat" :size="40"/></span>
          <p>暂无消息</p>
        </div>
        <div v-else class="convList">
          <div
            v-for="c in conversations" :key="c._virtual ? 'new-' + c.productId : c.productId + '-' + c.otherUserId"
            :class="['convItem', { active: activeProductId === c.productId && activeOther === c.otherUserId }]"
            @click="openChat(c)"
          >
            <div class="convAvatar" :style="c.otherUserAvatar ? { backgroundImage: `url(${c.otherUserAvatar})` } : {}">
              <span v-if="!c.otherUserAvatar">{{ displayName(c)[0] }}</span>
              <span v-if="c.unreadCount" class="unreadDot" />
            </div>
            <div class="convMeta">
              <div class="convTop">
                <span class="convName" @click.stop="router.push('/seller/' + c.otherUserId)">{{ displayName(c) }}</span>
                <span v-if="c.unreadCount" class="convBadge">{{ c.unreadCount > 99 ? '99+' : c.unreadCount }}</span>
                <span class="convTime">{{ formatTime(c.lastMessageTime) }}</span>
              </div>
              <div class="convPreview">{{ c.lastMessage?.substring(0, 40) }}</div>
              <div class="convProduct" @click.stop="router.push('/products/' + c.productId)">{{ c.productTitle }}</div>
            </div>
          </div>
        </div>
      </template>

      <!-- 系统消息 -->
      <template v-if="activeTab === 'system'">
        <p v-if="sysLoading" class="muted">加载中...</p>
        <div v-else-if="!systemMessages.length" class="emptyList">
          <span class="emptyIcon"><AppIcon name="package" :size="40"/></span>
          <p>暂无系统消息</p>
        </div>
        <div v-else class="sysList">
          <div
            v-for="m in systemMessages" :key="m.id"
            class="sysItem"
            @click="router.push('/orders/' + m.relatedId)"
          >
            <div class="sysIcon"><AppIcon name="package" :size="18"/></div>
            <div class="sysMeta">
              <div class="sysTitle">{{ m.title }}</div>
              <div class="sysContent">{{ m.content }}</div>
              <div class="sysTime">{{ m.time?.substring(0, 16) }}</div>
            </div>
          </div>
        </div>
      </template>

      <!-- 评论通知 -->
      <template v-if="activeTab === 'comments'">
        <p v-if="commentLoading" class="muted">加载中...</p>
        <div v-else-if="!commentNotifications.length" class="emptyList">
          <span class="emptyIcon"><AppIcon name="edit" :size="40"/></span>
          <p>暂无评论通知</p>
        </div>
        <div v-else class="notifList">
          <div
            v-for="c in commentNotifications" :key="c.id"
            class="notifItem"
            @click="router.push('/products/' + c.productId)"
          >
            <div class="notifAvatar" :style="c.commenterAvatar ? { backgroundImage: `url(${c.commenterAvatar})` } : {}">
              <span v-if="!c.commenterAvatar">{{ c.commenterName[0] }}</span>
            </div>
            <div class="notifMeta">
              <div class="notifTop">
                <span class="notifName">{{ c.commenterName }}</span>
                <span class="notifTime">{{ formatTime(c.time) }}</span>
              </div>
              <div class="notifContent">{{ c.content }}</div>
              <div class="notifProduct"><AppIcon name="package" :size="14"/> {{ c.productTitle }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 右侧面板：聊天窗口 -->
    <div class="rightPanel">
      <template v-if="activeTab === 'chat' && activeProductId && activeConv">
        <div class="chatHead">
          <button class="mobileBack" @click="activeProductId = null; activeOther = null">← 返回</button>
          <div class="chatHeadLeft">
            <div class="chatHeadAvatar" :style="activeConv.otherUserAvatar ? { backgroundImage: `url(${activeConv.otherUserAvatar})` } : {}">
              <span v-if="!activeConv.otherUserAvatar">{{ displayName(activeConv)[0] }}</span>
            </div>
            <div class="chatHeadInfo">
              <span class="chatHeadName" @click="router.push('/seller/' + activeOther)">{{ displayName(activeConv) }}</span>
              <span class="chatHeadProduct" @click="router.push('/products/' + activeProductId)"><AppIcon name="package" :size="14"/> {{ activeConv.productTitle }}</span>
            </div>
          </div>
        </div>

        <div class="chatBody" ref="chatBody">
          <div v-if="!messages.length" class="emptyChat">
            <span class="emptyChatIcon"><AppIcon name="chat" :size="40"/></span>
            <p>暂无消息</p>
            <p class="emptyChatHint">发送第一条消息开始对话吧</p>
          </div>
          <div v-for="m in messages" :key="m.id" :class="['bubbleWrap', m.senderId === currentUserId ? 'mine' : 'theirs']">
            <div :class="['bubble', m.senderId === currentUserId ? 'mine' : 'theirs']">
              <span class="bubbleText">{{ m.content }}</span>
            </div>
            <span class="bubbleTime">{{ formatTime(m.createdAt) }}</span>
          </div>
        </div>

        <div class="chatInput">
          <input v-model="text" placeholder="输入消息..." @keyup.enter="sendMessage" :disabled="sending" />
          <button class="sendBtn" :disabled="sending || !text.trim()" @click="sendMessage">
            {{ sending ? '...' : '发送' }}
          </button>
        </div>
        <p v-if="chatError" class="chatErrorMsg">{{ chatError }}</p>
      </template>

      <!-- 空状态（未选择对话） -->
      <div v-else class="noChat">
        <div class="noChatIcon">
          <AppIcon v-if="activeTab === 'system'" name="package" :size="48"/>
          <AppIcon v-else-if="activeTab === 'comments'" name="edit" :size="48"/>
          <AppIcon v-else name="chat" :size="48"/>
        </div>
        <p class="noChatTitle">
          {{ activeTab === 'system' ? '选择左侧系统消息查看详情' : activeTab === 'comments' ? '选择左侧评论查看对应商品' : '选择左侧对话开始聊天' }}
        </p>
        <p class="noChatHint">与卖家和买家沟通，让交易更顺畅</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msgPage {
  display: flex;
  height: calc(100vh - 120px);
  max-width: 960px;
  margin: 0 auto;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.panelTitle {
  margin: 0;
  padding: var(--space-lg) var(--space-lg) 0;
  font-size: 16px;
  font-weight: 700;
}

/* ---- 左侧面板 ---- */
.leftPanel {
  width: 340px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
}

/* Tabs */
.msgTabs {
  display: flex;
  padding: var(--space-md) var(--space-lg);
  gap: 4px;
}

.msgTab {
  flex: 1;
  border: none;
  background: var(--bg-secondary);
  padding: 7px 0;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
  transition: all var(--transition-fast);
}

.msgTab:hover { color: var(--text-secondary); }

.msgTab.active {
  background: var(--brand-gradient);
  color: white;
  font-weight: 600;
}

/* 对话列表 */
.convList { flex: 1; overflow-y: auto; }

.convItem {
  display: flex;
  gap: var(--space-md);
  align-items: flex-start;
  padding: var(--space-md) var(--space-lg);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.convItem:hover { background: var(--bg-hover); }
.convItem.active { background: var(--brand-light); }

.convAvatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 2px;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-tertiary);
  position: relative;
}

.unreadDot {
  position: absolute;
  top: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--error);
  border: 2px solid var(--bg-primary);
}

.convMeta { flex: 1; min-width: 0; display: grid; gap: 2px; }

.convTop {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.convName {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
}

.convName:hover { color: var(--brand-darker); text-decoration: underline; }

.convBadge {
  background: var(--error);
  color: white;
  font-size: 10px;
  font-weight: 700;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
}

.convTime {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-left: auto;
  white-space: nowrap;
}

.convPreview {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.convProduct {
  font-size: 11px;
  color: var(--text-disabled);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.convProduct:hover { text-decoration: underline; color: var(--text-tertiary); }

/* 系统消息 */
.sysList { flex: 1; overflow-y: auto; }

.sysItem {
  display: flex;
  gap: var(--space-md);
  align-items: flex-start;
  padding: var(--space-md) var(--space-lg);
  cursor: pointer;
  transition: background var(--transition-fast);
  border-bottom: 1px solid var(--border-light);
}

.sysItem:hover { background: var(--bg-hover); }

.sysIcon {
  font-size: 24px;
  flex-shrink: 0;
  margin-top: 2px;
}

.sysMeta { flex: 1; min-width: 0; display: grid; gap: 2px; }

.sysTitle { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.sysContent { font-size: 12px; color: var(--text-tertiary); }
.sysTime { font-size: 11px; color: var(--text-disabled); }

/* 评论通知 */
.notifList { flex: 1; overflow-y: auto; }

.notifItem {
  display: flex;
  gap: var(--space-md);
  align-items: flex-start;
  padding: var(--space-md) var(--space-lg);
  cursor: pointer;
  transition: background var(--transition-fast);
  border-bottom: 1px solid var(--border-light);
}

.notifItem:hover { background: var(--bg-hover); }

.notifAvatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 2px;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--text-tertiary);
}

.notifMeta { flex: 1; min-width: 0; display: grid; gap: 2px; }

.notifTop { display: flex; align-items: center; gap: var(--space-sm); }

.notifName { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.notifTime { font-size: 11px; color: var(--text-tertiary); margin-left: auto; }
.notifContent { font-size: 12px; color: var(--text-secondary); line-height: 1.4; }
.notifProduct { font-size: 11px; color: var(--text-disabled); }

/* 空列表 */
.emptyList {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  gap: var(--space-sm);
}

.emptyIcon { font-size: 40px; opacity: 0.6; }
.emptyList p { font-size: 13px; }

/* ---- 右侧面板 ---- */
.rightPanel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 聊天头部 */
.chatHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-tertiary);
}

.chatHeadLeft {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.chatHeadAvatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--bg-secondary);
  background-position: center;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--text-tertiary);
}

.chatHeadInfo { display: grid; gap: 1px; }

.chatHeadName {
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  color: var(--text-primary);
}

.chatHeadName:hover { color: var(--brand-darker); text-decoration: underline; }

.chatHeadProduct {
  font-size: 11px;
  color: var(--text-tertiary);
  cursor: pointer;
}

.chatHeadProduct:hover { text-decoration: underline; }

/* 聊天消息区 */
.chatBody {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  background: var(--bg-tertiary);
}

.emptyChat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.emptyChatIcon { font-size: 48px; margin-bottom: var(--space-sm); opacity: 0.5; }
.emptyChat p { font-size: 14px; }
.emptyChatHint { font-size: 12px !important; color: var(--text-disabled); margin-top: var(--space-xs); }

/* 气泡 */
.bubbleWrap {
  display: flex;
  flex-direction: column;
  max-width: 72%;
  animation: fadeInUp 0.25s ease both;
}

.bubbleWrap.mine { align-self: flex-end; align-items: flex-end; }
.bubbleWrap.theirs { align-self: flex-start; align-items: flex-start; }

.bubble {
  padding: 10px 16px;
  border-radius: var(--radius-lg);
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.bubble.mine {
  background: var(--brand-gradient);
  color: white;
  border-bottom-right-radius: var(--radius-xs);
}

.bubble.theirs {
  background: var(--bg-primary);
  color: var(--text-primary);
  border-bottom-left-radius: var(--radius-xs);
  border: 1px solid var(--border-light);
}

.bubbleText { white-space: pre-wrap; }

.bubbleTime {
  font-size: 10px;
  color: var(--text-disabled);
  margin-top: 4px;
  padding: 0 4px;
}

/* 输入区 */
.chatInput {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--border-light);
  background: var(--bg-primary);
}

.chatInput input {
  flex: 1;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-full);
  padding: 10px 16px;
  font-size: 13px;
  color: var(--text-primary);
  background: var(--bg-secondary);
}

.chatInput input:focus {
  background: var(--bg-primary);
  border-color: var(--brand) !important;
}

.sendBtn {
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 10px 24px;
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
  transition: all var(--transition-fast);
}

.sendBtn:hover:not(:disabled) { opacity: 0.9; }

.sendBtn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.chatErrorMsg {
  color: var(--error);
  font-size: 12px;
  padding: 4px var(--space-lg);
  margin: 0;
}

/* 未选择对话 */
.noChat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  background: var(--bg-tertiary);
}

.noChatIcon { font-size: 56px; opacity: 0.4; }

.noChatTitle {
  font-size: 15px;
  color: var(--text-tertiary);
}

.noChatHint {
  font-size: 12px;
  color: var(--text-disabled);
}

/* 通用 */
.muted {
  color: var(--text-tertiary);
  padding: var(--space-xl);
  text-align: center;
  font-size: 13px;
}

.error {
  color: var(--error);
  font-size: 12px;
  margin: 0;
  padding: var(--space-sm) var(--space-lg);
}

/* 移动端返回按钮 */
.mobileBack {
  display: none;
  border: none;
  background: none;
  font-size: 15px;
  color: var(--brand);
  cursor: pointer;
  padding: 4px 8px 4px 0;
  margin-right: 8px;
}

/* 响应式 */
@media (max-width: 700px) {
  .msgPage { flex-direction: column; height: auto; min-height: calc(100vh - 140px); border-radius: 0; border: none; }

  .mobileBack { display: block; }

  .leftPanel {
    width: 100%;
    flex: 1;
    border-right: none;
  }

  .rightPanel {
    position: fixed;
    inset: 0;
    top: 50px;
    z-index: 80;
    background: var(--bg-primary);
    display: none;
  }

  /* 当有活跃聊天时，隐藏左面板，显示右面板 */
  .msgPage.hasChat .leftPanel { display: none; }
  .msgPage.hasChat .rightPanel { display: flex; }

  .convItem { padding: var(--space-sm) var(--space-md); }
  .panelTitle { padding: var(--space-md) var(--space-md) 0; font-size: 15px; }
}
</style>

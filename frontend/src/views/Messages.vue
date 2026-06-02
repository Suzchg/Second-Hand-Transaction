<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { api } from '../api.js'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
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

const currentUserId = Number(localStorage.getItem('userId') || '0')

let timer = null

// ---- Conversation list ----
async function loadConversations() {
  try {
    conversations.value = await api('/api/users/messages') || []
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
    await loadConversations() // refresh badges
  } catch (e) { chatError.value = e.message }
  finally { sending.value = false }
}

async function openChat(conv) {
  activeProductId.value = conv.productId
  activeOther.value = conv.otherUserId
  await loadMessages()
  scrollBottom()
  // Mark as read
  try {
    await api('/api/messages/read', {
      method: 'PUT',
      body: { productId: conv.productId, otherUserId: conv.otherUserId },
    })
    await loadConversations() // refresh badges
  } catch { /* ignore */ }
}

function scrollBottom() {
  nextTick(() => {
    const el = chatBody.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// Auto-open conversation from URL query param
watch(() => route.query.product, async (pid) => {
  if (pid && conversations.value.length) {
    const conv = conversations.value.find(c => c.productId === Number(pid))
    if (conv) openChat(conv)
  }
})

// Translate conversation data for display
function displayName(conv) {
  return conv.otherUserName || '未知用户'
}

const activeConv = computed(() =>
  conversations.value.find(c => c.productId === activeProductId.value)
)

onMounted(async () => {
  await loadConversations()
  const pid = route.query.product
  if (pid) {
    const conv = conversations.value.find(c => c.productId === Number(pid))
    if (conv) openChat(conv)
  }
  timer = setInterval(async () => {
    await loadConversations()
    if (activeProductId.value) await loadMessages()
  }, 3000)
})
onUnmounted(() => { clearInterval(timer) })
</script>

<template>
  <div class="page">
    <!-- Left panel: conversation list -->
    <div class="leftPanel">
      <h2>消息</h2>
      <p v-if="loading" class="muted">加载中...</p>
      <p v-else-if="error" class="error">{{ error }}</p>
      <div v-else-if="!conversations.length" class="muted">暂无消息</div>
      <div v-else class="convList">
        <div
          v-for="c in conversations" :key="c.productId"
          :class="['convItem', { active: activeProductId === c.productId }]"
          @click="openChat(c)"
        >
          <div class="convAvatar" :style="c.otherUserAvatar ? { backgroundImage: `url(${c.otherUserAvatar})` } : {}">
            <span v-if="!c.otherUserAvatar">{{ displayName(c)[0] }}</span>
          </div>
          <div class="convMeta">
            <div class="convTop">
              <span class="convName">{{ displayName(c) }}</span>
              <span v-if="c.unreadCount" class="badge">{{ c.unreadCount }}</span>
              <span class="convTime">{{ c.lastMessageTime?.substring(5, 16) }}</span>
            </div>
            <div class="convPreview">{{ c.lastMessage?.substring(0, 40) }}</div>
            <div class="convTitle" @click.stop="router.push('/products/' + c.productId)">{{ c.productTitle }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Right panel: chat window -->
    <div class="rightPanel">
      <template v-if="activeProductId && activeConv">
        <div class="chatHead">
          <span>{{ displayName(activeConv) }}</span>
          <span class="chatProduct" @click="router.push('/products/' + activeProductId)">{{ activeConv.productTitle }}</span>
        </div>
        <div class="chatBody" ref="chatBody">
          <div v-if="!messages.length" class="empty">暂无消息，发送第一条消息吧</div>
          <div v-for="m in messages" :key="m.id" :class="['bubble', m.senderId === currentUserId ? 'mine' : 'theirs']">
            <div class="bubbleContent">{{ m.content }}</div>
            <div class="bubbleTime">{{ m.createdAt?.substring(5, 16) }}</div>
          </div>
        </div>
        <div class="chatInput">
          <input v-model="text" placeholder="输入消息..." @keyup.enter="sendMessage" :disabled="sending" />
          <button class="sendBtn" :disabled="sending" @click="sendMessage">发送</button>
        </div>
        <p v-if="chatError" class="error" style="padding:4px 12px">{{ chatError }}</p>
      </template>
      <div v-else class="noChat">
        <div class="noChatIcon">💬</div>
        <p>选择左侧对话开始聊天</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex; height: calc(100vh - 80px); gap: 0;
  max-width: 900px; margin: 0 auto;
  border: 1px solid rgba(0,0,0,0.08); border-radius: 16px;
  overflow: hidden; background: white;
}
h2 { margin: 0 0 12px; font-size: 18px; padding: 14px 14px 0; }

/* Left panel */
.leftPanel {
  width: 320px; flex-shrink: 0; border-right: 1px solid rgba(0,0,0,0.08);
  display: flex; flex-direction: column;
}
.convList { flex: 1; overflow-y: auto; }
.convItem {
  display: flex; gap: 10px; align-items: flex-start;
  padding: 10px 14px; cursor: pointer; transition: background 0.1s;
}
.convItem:hover { background: rgba(0,0,0,0.02); }
.convItem.active { background: rgba(0,0,0,0.05); }
.convAvatar {
  width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0; margin-top: 2px;
  background-color: #e0e0e0; background-position: center; background-size: cover; background-repeat: no-repeat;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; color: rgba(0,0,0,0.4);
}
.convMeta { flex: 1; min-width: 0; display: grid; gap: 1px; }
.convTop { display: flex; align-items: center; gap: 6px; }
.convName { font-size: 13px; font-weight: 600; }
.badge { background: #e65100; color: white; font-size: 10px; padding: 1px 5px; border-radius: 8px; font-weight: 600; }
.convTime { font-size: 10px; color: rgba(0,0,0,0.35); margin-left: auto; white-space: nowrap; }
.convPreview { font-size: 12px; color: rgba(0,0,0,0.45); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.convTitle { font-size: 10px; color: rgba(0,0,0,0.3); cursor: pointer; }
.convTitle:hover { text-decoration: underline; }

/* Right panel */
.rightPanel {
  flex: 1; display: flex; flex-direction: column; min-width: 0;
}
.chatHead {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid rgba(0,0,0,0.08);
  font-size: 14px; font-weight: 600;
}
.chatProduct { font-size: 12px; color: rgba(0,0,0,0.35); cursor: pointer; font-weight: 400; }
.chatProduct:hover { text-decoration: underline; }
.chatBody {
  flex: 1; overflow-y: auto; padding: 12px 16px;
  display: flex; flex-direction: column; gap: 10px;
}
.empty { text-align: center; color: rgba(0,0,0,0.3); margin-top: 60px; font-size: 13px; }
.bubble { max-width: 70%; display: grid; gap: 1px; }
.bubble.mine { align-self: flex-end; }
.bubble.theirs { align-self: flex-start; }
.bubbleContent {
  padding: 8px 12px; border-radius: 16px; font-size: 13px; line-height: 1.45;
  white-space: pre-wrap; word-break: break-word;
}
.bubble.mine .bubbleContent { background: #1677ff; color: white; border-bottom-right-radius: 4px; }
.bubble.theirs .bubbleContent { background: #f0f0f0; color: rgba(0,0,0,0.85); border-bottom-left-radius: 4px; }
.bubbleTime { font-size: 10px; color: rgba(0,0,0,0.25); padding: 0 4px; }
.bubble.mine .bubbleTime { text-align: right; }
.chatInput {
  display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid rgba(0,0,0,0.08);
}
.chatInput input {
  flex: 1; border: 1px solid rgba(0,0,0,0.1); border-radius: 10px;
  padding: 8px 10px; outline: none; font-size: 13px;
}
.sendBtn {
  border: 0; background: #1677ff; color: white;
  padding: 8px 16px; border-radius: 10px; cursor: pointer; font-size: 13px; font-weight: 500;
}
.sendBtn:disabled { opacity: 0.5; cursor: not-allowed; }
.noChat {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: rgba(0,0,0,0.25);
}
.noChatIcon { font-size: 48px; margin-bottom: 8px; }
.noChat p { font-size: 14px; }

.muted { color: rgba(0,0,0,0.4); padding: 20px; text-align: center; font-size: 13px; }
.error { color: #b00020; font-size: 12px; margin: 0; }
</style>

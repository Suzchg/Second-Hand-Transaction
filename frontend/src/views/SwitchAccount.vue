<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const accounts = ref([])
const currentId = ref(Number(localStorage.getItem('userId') || '0'))

function loadAccounts() {
  try {
    accounts.value = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
    currentId.value = Number(localStorage.getItem('userId') || '0')
  } catch { accounts.value = [] }
}

function switchTo(acc) {
  if (!acc.token) {
    alert('该账号的登录已过期，请重新登录')
    return
  }
  localStorage.setItem('token', acc.token)
  localStorage.setItem('userId', String(acc.userId))
  localStorage.setItem('nickname', acc.nickname)
  localStorage.setItem('role', acc.role || 'USER')
  window.location.replace('/')
}

function removeAccount(acc) {
  const list = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
  const filtered = list.filter(a => a.userId !== acc.userId)
  localStorage.setItem('savedAccounts', JSON.stringify(filtered))
  loadAccounts()
  // 如果删除的是当前账号，清除登录状态
  if (acc.userId === currentId.value) {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('role')
    router.push('/login')
  }
}

onMounted(loadAccounts)
</script>

<template>
  <div class="page">
    <h2>切换账号</h2>
    <p v-if="!accounts.length" class="muted">暂无已保存的账号</p>
    <div v-else class="list">
      <div v-for="acc in accounts" :key="acc.userId" class="card" @click="switchTo(acc)">
        <div class="left">
          <div class="name">
            {{ acc.nickname }}
            <span v-if="acc.userId === currentId" class="current">当前</span>
          </div>
          <div class="id">{{ acc.identifier }}</div>
        </div>
        <button class="del" @click.stop="removeAccount(acc)">删除</button>
      </div>
    </div>
    <button class="btn" @click="router.push('/login')">+ 添加账号</button>
  </div>
</template>

<style scoped>
.page { max-width: 400px; margin: 0 auto; }
h2 { margin: 0 0 16px; font-size: 20px; }
.list { display: grid; gap: 8px; margin-bottom: 14px; }
.card {
  border: 1px solid rgba(0,0,0,0.08); border-radius: 14px;
  padding: 14px 16px; background: white; cursor: pointer;
  display: flex; justify-content: space-between; align-items: center;
  transition: background 0.12s;
}
.card:hover { background: rgba(0,0,0,0.02); }
.name { font-weight: 600; font-size: 15px; display: flex; align-items: center; gap: 8px; }
.current { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: #e8f5e9; color: #2e7d32; }
.id { font-size: 12px; color: rgba(0,0,0,0.4); margin-top: 2px; }
.del {
  border: 0; background: none; color: #b00020; cursor: pointer;
  font-size: 12px; padding: 4px 8px;
}
.btn {
  width: 100%; border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 10px; border-radius: 12px; cursor: pointer; font-size: 14px;
}
.muted { color: rgba(0,0,0,0.45); font-size: 13px; }
</style>

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
  localStorage.setItem('avatarUrl', acc.avatarUrl || '')
  window.location.replace('/')
}

function removeAccount(acc) {
  const list = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
  const filtered = list.filter(a => a.userId !== acc.userId)
  localStorage.setItem('savedAccounts', JSON.stringify(filtered))
  loadAccounts()
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
    <h2><AppIcon name="refresh" :size="20"/> 切换账号</h2>

    <p v-if="!accounts.length" class="emptyText">暂无已保存的账号</p>

    <div v-else class="accountList">
      <div v-for="acc in accounts" :key="acc.userId" class="accountCard" @click="switchTo(acc)">
        <div class="accLeft">
          <div class="accAvatar">{{ acc.nickname[0] }}</div>
          <div class="accInfo">
            <div class="accName">
              {{ acc.nickname }}
              <span v-if="acc.userId === currentId" class="currentBadge">当前</span>
            </div>
            <div class="accId">{{ acc.identifier }}</div>
          </div>
        </div>
        <button class="delBtn" @click.stop="removeAccount(acc)">删除</button>
      </div>
    </div>

    <button class="addBtn" @click="router.push('/login')">+ 添加账号</button>
  </div>
</template>

<style scoped>
.page { max-width: 420px; margin: 0 auto; }

h2 { margin: 0 0 var(--space-xl); font-size: 20px; font-weight: 700; }

.accountList { display: grid; gap: var(--space-sm); margin-bottom: var(--space-lg); }

.accountCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all var(--transition-fast);
}

.accountCard:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-sm);
}

.accLeft { display: flex; align-items: center; gap: var(--space-md); }

.accAvatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
}

.accInfo { display: grid; gap: 2px; }

.accName { font-weight: 600; font-size: 15px; display: flex; align-items: center; gap: var(--space-sm); }

.currentBadge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--success-bg);
  color: var(--success);
  font-weight: 600;
}

.accId { font-size: 12px; color: var(--text-tertiary); }

.delBtn {
  border: none;
  background: none;
  color: var(--error);
  cursor: pointer;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.delBtn:hover { background: var(--error-bg); }

.addBtn {
  width: 100%;
  border: 1.5px dashed var(--border-default);
  background: var(--bg-primary);
  padding: 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.addBtn:hover {
  border-color: var(--brand-dark);
  color: var(--brand-darker);
  background: var(--brand-light);
}

.emptyText { color: var(--text-tertiary); font-size: 13px; text-align: center; padding: 30px; }
</style>

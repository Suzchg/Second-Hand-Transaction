<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api.js'

const users = ref({ content: [] })
const keyword = ref('')
const msg = ref('')

async function load(p = 0) {
  try {
    let url = `/api/admin/users?page=${p}&size=20`
    if (keyword.value) url += `&keyword=${encodeURIComponent(keyword.value)}`
    users.value = await api(url)
  } catch (e) { msg.value = e.message }
}

async function toggleDisable(u) {
  const action = u.status === 'ACTIVE' ? '禁用' : '启用'
  if (!confirm(`确定${action}用户「${u.nickname}」？`)) return
  try {
    await api(`/api/admin/users/${u.id}/disable?disabled=${u.status === 'ACTIVE'}`, { method: 'PUT' })
    msg.value = `${action}成功`
    await load(users.value.number)
  } catch (e) { msg.value = e.message }
}

async function kick(u) {
  if (!confirm(`确定强制下线「${u.nickname}」？`)) return
  try {
    await api(`/api/admin/users/${u.id}/kick`, { method: 'POST' })
    msg.value = '已强制下线'
  } catch (e) { msg.value = e.message }
}

onMounted(() => load())
</script>

<template>
  <div>
    <h2>用户管理</h2>
    <div class="bar">
      <input v-model="keyword" placeholder="搜索昵称..." @keyup.enter="load()" />
      <button class="btn" @click="load()">搜索</button>
    </div>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <table>
      <thead><tr><th>ID</th><th>昵称</th><th>角色</th><th>状态</th><th>注册时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="u in users.content" :key="u.id">
          <td>{{ u.id }}</td>
          <td>{{ u.nickname }}</td>
          <td><span :class="u.role === 'ADMIN' ? 'tag admin' : 'tag'">{{ u.role }}</span></td>
          <td><span :class="u.status === 'ACTIVE' ? 'tag on' : 'tag off'">{{ u.status === 'ACTIVE' ? '正常' : '禁用' }}</span></td>
          <td class="time">{{ u.createdAt?.substring(0,10) }}</td>
          <td class="actions">
            <button class="act" @click="toggleDisable(u)">{{ u.status === 'ACTIVE' ? '禁用' : '启用' }}</button>
            <button class="act danger" @click="kick(u)">强制下线</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager" v-if="users.totalPages > 1">
      <button :disabled="users.first" @click="load(users.number - 1)">上一页</button>
      <span>{{ users.number + 1 }} / {{ users.totalPages }}</span>
      <button :disabled="users.last" @click="load(users.number + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
h2 { margin: 0 0 12px; font-size: 20px; color: var(--text-primary); }
.bar { display: flex; gap: 8px; margin-bottom: 10px; }
.bar input {
  padding: 7px 10px; border: 1px solid var(--border-default); border-radius: 8px;
  outline: none; width: 200px; background: var(--bg-primary); color: var(--text-primary);
}
.btn {
  padding: 7px 12px; border: 1px solid var(--border-default); border-radius: 8px;
  background: var(--bg-primary); cursor: pointer; color: var(--text-secondary);
}
.msg { padding: 6px 10px; background: var(--success-bg); border-radius: 8px; font-size: 13px; color: var(--success); }
table { width: 100%; border-collapse: collapse; font-size: 13px; color: var(--text-primary); }
th, td { padding: 8px 10px; text-align: left; border-bottom: 1px solid var(--border-light); }
th { font-weight: 600; color: var(--text-tertiary); font-size: 12px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 11px; background: var(--bg-secondary); color: var(--text-secondary); }
.tag.admin { background: var(--info-bg); color: var(--info); }
.tag.on { background: var(--success-bg); color: var(--success); }
.tag.off { background: var(--error-bg); color: var(--error); }
.time { color: var(--text-tertiary); white-space: nowrap; }
.actions { display: flex; gap: 6px; }
.act {
  padding: 4px 8px; border: 1px solid var(--border-default); border-radius: 6px;
  background: var(--bg-primary); cursor: pointer; font-size: 11px; color: var(--text-secondary);
}
.act.danger { color: var(--error); border-color: var(--error-border); }
.pager { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 13px; color: var(--text-primary); }
.pager button {
  padding: 6px 12px; border: 1px solid var(--border-default); border-radius: 8px;
  background: var(--bg-primary); cursor: pointer; color: var(--text-secondary);
}
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>

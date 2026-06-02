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
h2 { margin: 0 0 12px; font-size: 20px; }
.bar { display: flex; gap: 8px; margin-bottom: 10px; }
.bar input { padding: 7px 10px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; outline: none; width: 200px; }
.btn { padding: 7px 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; background: white; cursor: pointer; }
.msg { padding: 6px 10px; background: #e8f5e9; border-radius: 8px; font-size: 13px; color: #2e7d32; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px 10px; text-align: left; border-bottom: 1px solid rgba(0,0,0,0.05); }
th { font-weight: 600; color: rgba(0,0,0,0.5); font-size: 12px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 11px; background: rgba(0,0,0,0.06); }
.tag.admin { background: #e3f2fd; color: #1565c0; }
.tag.on { background: #e8f5e9; color: #2e7d32; }
.tag.off { background: #fce4ec; color: #b00020; }
.time { color: rgba(0,0,0,0.4); white-space: nowrap; }
.actions { display: flex; gap: 6px; }
.act {
  padding: 4px 8px; border: 1px solid rgba(0,0,0,0.12); border-radius: 6px;
  background: white; cursor: pointer; font-size: 11px;
}
.act.danger { color: #b00020; border-color: #b00020; }
.pager { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 13px; }
.pager button { padding: 6px 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 8px; background: white; cursor: pointer; }
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>

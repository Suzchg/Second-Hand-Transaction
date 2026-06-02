<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['select'])
const categories = ref([])
const activeId = ref(null)

onMounted(async () => {
  try {
    categories.value = await api('/api/categories', { auth: false })
  } catch { /* ignore */ }
})

function select(id) {
  activeId.value = activeId.value === id ? null : id
  emit('select', activeId.value)
}
</script>

<template>
  <aside class="nav" v-if="categories.length">
    <h3 class="title">分类</h3>
    <ul class="list">
      <li
        v-for="cat in categories"
        :key="cat.id"
        :class="['item', { active: activeId === cat.id }]"
        @click="select(cat.id)"
      >
        <span class="name">{{ cat.name }}</span>
        <span class="count" v-if="cat.children?.length">{{ cat.children.length }}</span>
      </li>
    </ul>
    <!-- 子分类 -->
    <div v-if="activeId" class="sub">
      <template v-for="cat in categories" :key="cat.id">
        <div v-if="cat.id === activeId" class="subList">
          <span
            v-for="child in cat.children"
            :key="child.id"
            class="subItem"
          >{{ child.name }}</span>
        </div>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.nav {
  width: 160px;
  flex-shrink: 0;
}
.title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
}
.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 4px;
}
.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.12s;
}
.item:hover { background: rgba(0,0,0,0.04); }
.item.active { background: rgba(0,0,0,0.08); font-weight: 600; }
.name { color: rgba(0,0,0,0.8); }
.count { font-size: 11px; color: rgba(0,0,0,0.4); }
.sub {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0,0,0,0.06);
}
.subList {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.subItem {
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(0,0,0,0.04);
  font-size: 12px;
  color: rgba(0,0,0,0.6);
}
</style>

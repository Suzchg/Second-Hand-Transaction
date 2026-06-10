<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['select'])
const categories = ref([])
const activeId = ref(null)

// 分类名称 → AppIcon 名称映射
const iconMap = {
  '手机通讯': 'phone', '电脑办公': 'monitor', '数码影音': 'camera',
  '家用电器': 'zap', '家具家装': 'home', '家居日用': 'home',
  '男装': 'user', '女装': 'user', '鞋靴箱包': 'shopping-bag',
  '珠宝配饰': 'star', '美妆护肤': 'sparkle', '母婴亲子': 'heart',
  '运动户外': 'activity', '图书音像': 'book', '食品生鲜': 'shopping-bag',
  '医药保健': 'shield', '汽车用品': 'truck', '宠物生活': 'heart',
  '文玩收藏': 'star', '乐器/音乐': 'music', '潮玩/模型': 'package',
  '游戏/电竞': 'gamepad', '票券/其他': 'package',
}

function getIconName(cat) {
  return iconMap[cat.name] || 'package'
}

onMounted(async () => {
  try {
    categories.value = await api('/api/categories', { auth: false })
  } catch { /* ignore */ }
})

function select(id) {
  if (activeId.value === id) {
    activeId.value = null
    emit('select', 0)
    return
  }
  activeId.value = id
  emit('select', id)
}

function selectRecommend() {
  activeId.value = null
  emit('select', 0)
}
</script>

<template>
  <aside class="nav" v-if="categories.length">
    <h3 class="navTitle"><AppIcon name="folder" :size="16"/> 分类浏览</h3>
    <ul class="navList">
      <!-- 推荐项 -->
      <li
        :class="['navItem', 'recommendItem', { active: activeId === null }]"
        @click="selectRecommend()"
      >
        <span class="navIcon"><AppIcon name="home" :size="18"/></span>
        <span class="navLabel">主页推荐</span>
        <span v-if="activeId === null" class="navArrow">›</span>
      </li>
      <!-- 分类列表 -->
      <li
        v-for="cat in categories"
        :key="cat.id"
        :class="['navItem', { active: activeId === cat.id }]"
        @click="select(cat.id)"
      >
        <span class="navIcon"><AppIcon :name="getIconName(cat)" :size="18"/></span>
        <span class="navLabel">{{ cat.name }}</span>
        <span v-if="activeId === cat.id" class="navArrow">›</span>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.nav {
  width: 180px;
  flex-shrink: 0;
}

.navTitle {
  margin: 0 0 var(--space-md);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  padding: 0 var(--space-sm);
}

.navList {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 2px;
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  padding: var(--space-sm);
  border: 1px solid var(--border-light);
}

.navItem {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
  position: relative;
}

.navItem:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.navItem.active {
  background: var(--brand-light);
  color: var(--brand-darker);
  font-weight: 600;
}

.recommendItem {
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 2px;
  padding-bottom: 12px;
  color: var(--brand-darker);
  font-weight: 500;
}

.navIcon {
  width: 22px;
  text-align: center;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.navLabel {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navArrow {
  font-size: 16px;
  color: var(--brand-dark);
  font-weight: 600;
}

/* 响应式 */
@media (max-width: 640px) {
  .nav {
    width: 100%;
    overflow-x: auto;
  }

  .navList {
    display: flex;
    flex-direction: row;
    gap: var(--space-sm);
    padding: var(--space-sm);
    overflow-x: auto;
    white-space: nowrap;
    -webkit-overflow-scrolling: touch;
  }

  .navItem {
    flex-shrink: 0;
    white-space: nowrap;
    padding: 8px 14px;
    font-size: 12px;
  }

  .recommendItem {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 8px;
  }

  .navArrow {
    display: none;
  }
}
</style>

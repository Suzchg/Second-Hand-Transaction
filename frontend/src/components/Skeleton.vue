<script setup>
defineProps({
  // 骨架类型：card | list | text | image
  type: {
    type: String,
    default: 'text',
    validator: v => ['card', 'list', 'text', 'image', 'circle'].includes(v),
  },
  // 宽度比例：1-100
  width: { type: Number, default: 100 },
  // 高度（px）
  height: { type: Number, default: 16 },
})
</script>

<template>
  <!-- 骨架卡片（商品卡片占位） -->
  <div v-if="type === 'card'" class="skCard">
    <div class="skImage" />
    <div class="skBody">
      <div class="skLine" style="width: 85%" />
      <div class="skLine" style="width: 45%" />
    </div>
  </div>

  <!-- 骨架列表项 -->
  <div v-else-if="type === 'list'" class="skList">
    <div class="skCircle" />
    <div class="skListBody">
      <div class="skLine" style="width: 60%" />
      <div class="skLine" style="width: 40%; height: 12px" />
    </div>
  </div>

  <!-- 骨架文本行 -->
  <div
    v-else-if="type === 'text'"
    class="skLine"
    :style="{ width: width + '%', height: height + 'px' }"
  />

  <!-- 骨架图片 -->
  <div
    v-else-if="type === 'image'"
    class="skImage"
    :style="{ height: height + 'px' }"
  />

  <!-- 骨架圆形（头像占位） -->
  <div
    v-else-if="type === 'circle'"
    class="skCircle"
    :style="{ width: height + 'px', height: height + 'px' }"
  />
</template>

<style scoped>
.skCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.skImage {
  background: linear-gradient(90deg, var(--bg-secondary) 25%, #e8e8e8 50%, var(--bg-secondary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  height: 160px;
}

.skBody {
  padding: var(--space-md);
  display: grid;
  gap: var(--space-sm);
}

.skLine {
  height: 14px;
  border-radius: var(--radius-xs);
  background: linear-gradient(90deg, var(--bg-secondary) 25%, #e8e8e8 50%, var(--bg-secondary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skList {
  display: flex;
  gap: var(--space-md);
  align-items: center;
  padding: var(--space-md);
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
}

.skListBody {
  flex: 1;
  display: grid;
  gap: var(--space-sm);
}

.skCircle {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(90deg, var(--bg-secondary) 25%, #e8e8e8 50%, var(--bg-secondary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>

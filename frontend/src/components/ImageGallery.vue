<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ productId: { type: Number, required: true } })
const images = ref([])
const active = ref(0)
const lightbox = ref(false)

onMounted(async () => {
  try {
    images.value = await api(`/api/products/${props.productId}/images`) || []
  } catch { /* ignore */ }
})

function prev() { active.value = active.value > 0 ? active.value - 1 : images.value.length - 1 }
function next() { active.value = active.value < images.value.length - 1 ? active.value + 1 : 0 }
function openLightbox() { lightbox.value = true }
function closeLightbox() { lightbox.value = false }
</script>

<template>
  <div v-if="images.length" class="gallery">
    <!-- 主图 -->
    <div class="mainImage" @click="openLightbox">
      <img :src="images[active].thumbnailUrl" :alt="'图片 ' + (active + 1)" loading="lazy" />
      <button v-if="images.length > 1" class="mainNav mainPrev" @click.stop="prev">‹</button>
      <button v-if="images.length > 1" class="mainNav mainNext" @click.stop="next">›</button>
      <div class="imageCounter" v-if="images.length > 1">{{ active + 1 }} / {{ images.length }}</div>
    </div>

    <!-- 缩略图列表 -->
    <div v-if="images.length > 1" class="thumbnailList">
      <div
        v-for="(img, i) in images"
        :key="img.id"
        :class="['thumbnail', { active: i === active }]"
        @click="active = i"
      >
        <img :src="img.thumbnailUrl" :alt="'缩略图 ' + (i + 1)" loading="lazy" />
      </div>
    </div>

    <!-- Lightbox -->
    <Teleport to="body">
      <div v-if="lightbox" class="lightboxOverlay" @click="closeLightbox">
        <button class="lbClose" @click="closeLightbox"><AppIcon name="close" :size="20"/></button>
        <button v-if="images.length > 1" class="lbNav lbPrev" @click.stop="prev">‹</button>
        <img :src="images[active].url" :alt="'原图 ' + (active + 1)" class="lbImage" @click.stop />
        <button v-if="images.length > 1" class="lbNav lbNext" @click.stop="next">›</button>
        <div class="lbCounter" v-if="images.length > 1">{{ active + 1 }} / {{ images.length }}</div>
      </div>
    </Teleport>
  </div>
  <div v-else class="noImage">
    <span class="noImageIcon"><AppIcon name="image" :size="48"/></span>
    <span>暂无图片</span>
  </div>
</template>

<style scoped>
.gallery {
  display: grid;
  gap: var(--space-md);
}

/* 主图 */
.mainImage {
  position: relative;
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  overflow: hidden;
  cursor: zoom-in;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  max-height: 500px;
}

.mainImage img {
  max-width: 100%;
  max-height: 500px;
  object-fit: contain;
  display: block;
}

.mainNav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
  opacity: 0;
}

.mainImage:hover .mainNav { opacity: 1; }

.mainNav:hover {
  background: white;
  color: var(--text-primary);
  box-shadow: var(--shadow-md);
}

.mainPrev { left: 12px; }
.mainNext { right: 12px; }

.imageCounter {
  position: absolute;
  bottom: 12px;
  right: 12px;
  background: rgba(0, 0, 0, 0.55);
  color: white;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  backdrop-filter: blur(4px);
}

/* 缩略图 */
.thumbnailList {
  display: flex;
  gap: var(--space-sm);
  overflow-x: auto;
  padding: var(--space-xs) 0;
}

.thumbnail {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  flex-shrink: 0;
  transition: all var(--transition-fast);
  background: var(--bg-secondary);
}

.thumbnail:hover {
  border-color: var(--border-strong);
}

.thumbnail.active {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px rgba(14, 181, 166, 0.25);
}

.thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 无图片 */
.noImage {
  height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.noImageIcon {
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.5;
}

/* Lightbox */
.lightboxOverlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
}

.lbClose {
  position: absolute;
  top: 16px;
  right: 20px;
  background: none;
  border: none;
  color: white;
  font-size: 28px;
  cursor: pointer;
  z-index: 2;
  opacity: 0.7;
  transition: opacity var(--transition-fast);
}

.lbClose:hover { opacity: 1; }

.lbImage {
  max-width: 92vw;
  max-height: 92vh;
  object-fit: contain;
  border-radius: var(--radius-xs);
  user-select: none;
}

.lbNav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  font-size: 32px;
  cursor: pointer;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  transition: background var(--transition-fast);
}

.lbNav:hover { background: rgba(255, 255, 255, 0.25); }

.lbPrev { left: 16px; }
.lbNext { right: 16px; }

.lbCounter {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
}

/* 响应式 */
@media (max-width: 640px) {
  .mainImage {
    min-height: 280px;
    max-height: 360px;
  }

  .mainNav { opacity: 1; width: 32px; height: 32px; font-size: 18px; }
  .mainPrev { left: 8px; }
  .mainNext { right: 8px; }

  .thumbnail { width: 52px; height: 52px; }
}
</style>

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
    <div class="main" @click="openLightbox">
      <img :src="images[active].thumbnailUrl" :alt="'图片 ' + (active + 1)" />
      <button v-if="images.length > 1" class="nav left" @click.stop="prev">‹</button>
      <button v-if="images.length > 1" class="nav right" @click.stop="next">›</button>
    </div>
    <div v-if="images.length > 1" class="dots">
      <span v-for="(img, i) in images" :key="img.id" :class="['dot', { on: i === active }]" @click="active = i" />
    </div>

    <!-- Lightbox -->
    <div v-if="lightbox" class="lightboxOverlay" @click="closeLightbox">
      <button class="closeBtn" @click="closeLightbox">✕</button>
      <button v-if="images.length > 1" class="lbNav lbLeft" @click.stop="prev">‹</button>
      <img :src="images[active].url" :alt="'原图 ' + (active + 1)" class="lightboxImg" @click.stop />
      <button v-if="images.length > 1" class="lbNav lbRight" @click.stop="next">›</button>
    </div>
  </div>
  <div v-else class="placeholder">暂无图片</div>
</template>

<style scoped>
.main {
  height: 340px; border-radius: 18px;
  background-color: #f3f3f3;
  position: relative; border: 1px solid rgba(0,0,0,0.08);
  overflow: hidden; cursor: zoom-in;
  display: flex; align-items: center; justify-content: center;
}
.main img {
  max-width: 100%; max-height: 100%;
  object-fit: contain;
}
.nav {
  position: absolute; top: 50%; transform: translateY(-50%);
  width: 36px; height: 36px; border-radius: 50%; border: 0;
  background: rgba(255,255,255,0.85); font-size: 22px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: rgba(0,0,0,0.6);
}
.left { left: 10px; }
.right { right: 10px; }
.dots { display: flex; justify-content: center; gap: 6px; margin-top: 10px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(0,0,0,0.15); cursor: pointer; }
.dot.on { background: rgba(0,0,0,0.5); }
.placeholder { height: 200px; display: flex; align-items: center; justify-content: center; color: rgba(0,0,0,0.3); }

/* Lightbox */
.lightboxOverlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(0,0,0,0.9);
  display: flex; align-items: center; justify-content: center;
}
.closeBtn {
  position: absolute; top: 16px; right: 20px;
  background: none; border: 0; color: white; font-size: 28px; cursor: pointer;
  z-index: 2;
}
.lightboxImg {
  max-width: 92vw; max-height: 92vh;
  object-fit: contain; border-radius: 4px;
}
.lbNav {
  position: absolute; top: 50%; transform: translateY(-50%);
  width: 48px; height: 48px; border-radius: 50%; border: 0;
  background: rgba(255,255,255,0.2); font-size: 30px; cursor: pointer;
  color: white; display: flex; align-items: center; justify-content: center;
  z-index: 2;
}
.lbLeft { left: 16px; }
.lbRight { right: 16px; }
</style>

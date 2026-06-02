<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ productId: { type: Number, required: true } })
const images = ref([])
const active = ref(0)

onMounted(async () => {
  try {
    images.value = await api(`/api/products/${props.productId}/images`) || []
  } catch { /* ignore */ }
})

function prev() { active.value = active.value > 0 ? active.value - 1 : images.value.length - 1 }
function next() { active.value = active.value < images.value.length - 1 ? active.value + 1 : 0 }
</script>

<template>
  <div v-if="images.length" class="gallery">
    <div class="main" :style="{ backgroundImage: `url(${images[active].url})` }">
      <button v-if="images.length > 1" class="nav left" @click="prev">‹</button>
      <button v-if="images.length > 1" class="nav right" @click="next">›</button>
    </div>
    <div v-if="images.length > 1" class="dots">
      <span v-for="(img, i) in images" :key="img.id" :class="['dot', { on: i === active }]" @click="active = i" />
    </div>
  </div>
  <div v-else class="placeholder">暂无图片</div>
</template>

<style scoped>
.main {
  height: 340px; border-radius: 18px;
  background-color: #f3f3f3; background-position: center; background-size: cover; background-repeat: no-repeat;
  position: relative; border: 1px solid rgba(0,0,0,0.08);
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
</style>

<script setup>
import { ref } from 'vue'
import { api } from '../api.js'

const props = defineProps({ productId: { type: Number, required: true } })
const emit = defineEmits(['change'])

const images = ref([])
const uploading = ref(false)
const error = ref('')
const MAX = 9

async function loadImages() {
  try {
    images.value = await api(`/api/products/${props.productId}/images`) || []
  } catch { /* ignore */ }
}

async function onFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  if (images.value.length >= MAX) { error.value = `最多上传 ${MAX} 张图片`; return }
  // 文件大小限制 10MB
  if (file.size > 10 * 1024 * 1024) { error.value = '图片不能超过 10MB'; return }
  const fd = new FormData()
  fd.append('file', file)
  uploading.value = true; error.value = ''
  try {
    await api(`/api/products/${props.productId}/images`, { method: 'POST', body: fd, raw: true })
    await loadImages()
    emit('change')
  } catch (err) {
    error.value = err.message || '上传失败'
  } finally {
    uploading.value = false
  }
  // 清空 input 以允许重复上传同一文件
  e.target.value = ''
}

async function remove(img) {
  if (!confirm('删除这张图片？')) return
  try {
    await api(`/api/products/${props.productId}/images/${img.id}`, { method: 'DELETE' })
    await loadImages()
    emit('change')
  } catch (err) {
    error.value = err.message || '删除失败'
  }
}

async function setCover(img) {
  await api(`/api/products/${props.productId}/images/${img.id}/cover`, { method: 'PUT' })
  await loadImages()
  emit('change')
}

loadImages()
defineExpose({ images })
</script>

<template>
  <div class="uploader">
    <div class="grid">
      <div v-for="(img, i) in images" :key="img.id" class="thumb" @click="setCover(img)">
        <img :src="img.thumbnailUrl" :alt="'image ' + i" />
        <span class="coverTag" v-if="i === 0">封面</span>
        <button class="del" @click.stop="remove(img)">×</button>
      </div>
      <label v-if="images.length < MAX" class="addBtn">
        <span class="plus">+</span>
        <span class="hint" v-if="uploading">上传中...</span>
        <span class="hint" v-else-if="images.length === 0">点击上传实物照片</span>
        <span class="hint" v-else>{{ images.length }}/{{ MAX }}</span>
        <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden @change="onFileChange" :disabled="uploading" />
      </label>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p class="tip">点击图片可设为封面；支持 JPG/PNG/WebP，单张不超过 10MB</p>
  </div>
</template>

<style scoped>
.grid { display: flex; flex-wrap: wrap; gap: 10px; }
.thumb {
  width: 100px; height: 100px; border-radius: var(--radius-md); overflow: hidden;
  position: relative; cursor: pointer; border: 1px solid var(--border-light);
  transition: transform var(--transition-fast);
}
.thumb:hover { transform: scale(1.03); }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.coverTag {
  position: absolute; bottom: 4px; left: 4px;
  padding: 2px 6px; border-radius: var(--radius-xs);
  background: rgba(0,0,0,0.6); color: white; font-size: 10px;
}
.del {
  position: absolute; top: 4px; right: 4px;
  width: 22px; height: 22px; border-radius: 50%; border: 0;
  background: rgba(0,0,0,0.45); color: white;
  font-size: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity var(--transition-fast);
}
.thumb:hover .del { opacity: 1; }
.addBtn {
  width: 100px; height: 100px;
  border: 2px dashed var(--border-default); border-radius: var(--radius-md);
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; cursor: pointer;
  transition: border-color var(--transition-fast); gap: 4px;
  text-align: center;
  background: var(--bg-tertiary);
}
.addBtn:hover { border-color: var(--brand-dark); background: var(--brand-light); }
.plus { font-size: 28px; color: var(--text-tertiary); }
.hint { font-size: 10px; color: var(--text-tertiary); padding: 0 6px; }
.error { color: var(--error); font-size: 12px; margin: 8px 0 0; }
.tip { color: var(--text-tertiary); font-size: 11px; margin: 8px 0 0; }
</style>

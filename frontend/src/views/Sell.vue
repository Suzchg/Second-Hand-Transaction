<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'
import ImageUploader from '../components/ImageUploader.vue'

const router = useRouter()
const title = ref('')
const priceYuan = ref('')
const description = ref('')
const quantity = ref(1)
const categoryId = ref(null)
const categories = ref([])
const loading = ref(false)
const error = ref('')
const createdId = ref(null)

onMounted(async () => {
  try { categories.value = await api('/api/categories', { auth: false }) || [] }
  catch { /* ignore */ }
})

function flatCats() {
  const result = []
  for (const root of categories.value) {
    result.push({ id: root.id, name: root.name, indent: '' })
    if (root.children) {
      for (const child of root.children)
        result.push({ id: child.id, name: child.name, indent: '  └ ' })
    }
  }
  return result
}

async function submit() {
  error.value = ''
  if (!title.value.trim()) { error.value = '请输入标题'; return }
  const priceCent = Math.round(Number(priceYuan.value || '0') * 100)
  if (!priceCent || priceCent <= 0) { error.value = '请输入有效价格'; return }
  if (!description.value.trim()) { error.value = '请输入描述'; return }
  loading.value = true
  try {
    const p = await api('/api/products', {
      method: 'POST',
      body: {
        title: title.value.trim(),
        priceCent,
        quantity: quantity.value,
        coverImageUrl: '',  // 不再需要填 URL，上传图片后自动设为封面
        description: description.value.trim(),
        categoryId: categoryId.value,
      },
    })
    createdId.value = p.id
  } catch (e) {
    error.value = e.message || '发布失败'
  } finally {
    loading.value = false
  }
}

function done() {
  router.push(`/products/${createdId.value}`)
}
</script>

<template>
  <div class="card">
    <h2 class="h2">发布商品</h2>

    <!-- Step 1: 填写信息 -->
    <div v-if="!createdId" class="form">
      <label>标题 <input v-model="title" placeholder="例如：二手 iPhone 13，九成新" /></label>
      <label>分类
        <select v-model="categoryId" class="sel">
          <option :value="null">请选择分类（可选）</option>
          <option v-for="c in flatCats()" :key="c.id" :value="c.id">{{ c.indent }}{{ c.name }}</option>
        </select>
      </label>
      <label>价格（元）<input v-model="priceYuan" type="number" step="0.01" min="0" placeholder="例如：1999.00" /></label>
      <label>库存数量 <input v-model.number="quantity" type="number" min="1" max="999" placeholder="默认 1" /></label>
      <label>描述 <textarea v-model="description" rows="4" placeholder="描述商品成色、规格、配件等信息" /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" :disabled="loading" @click="submit">
        {{ loading ? '创建中...' : '下一步：上传图片' }}
      </button>
    </div>

    <!-- Step 2: 上传图片 -->
    <div v-else>
      <p class="okMsg">✅ 商品已创建！上传实物图片（最多 9 张），第一张自动成为封面</p>
      <ImageUploader :productId="createdId" />
      <div class="actions">
        <button class="primary" @click="done">完成发布</button>
        <button class="ghost" @click="done">稍后再说</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  max-width: 520px;
  width: 100%;
  margin: 0 auto;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 18px;
  padding: 22px;
  background: white;
  box-sizing: border-box;
}
.h2 { margin: 0 0 14px; font-size: 20px; }
.form { display: grid; gap: 12px; width: 100%; }
label { display: grid; gap: 6px; font-size: 13px; color: rgba(0,0,0,0.7); width: 100%; }
input, textarea, .sel {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid rgba(0,0,0,0.12);
  border-radius: 12px;
  padding: 10px 12px;
  outline: none;
  font-family: inherit;
  font-size: 14px;
}
.sel { background: white; }
textarea { resize: vertical; min-height: 80px; }
.primary {
  border: 0; background: black; color: white;
  padding: 11px 14px; border-radius: 12px; cursor: pointer;
  font-size: 15px;
}
.primary:disabled { opacity: 0.5; cursor: not-allowed; }
.error { margin: 0; color: #b00020; font-size: 13px; }
.okMsg { color: #2e7d32; font-size: 14px; margin-bottom: 14px; }
.actions { margin-top: 16px; display: flex; gap: 10px; align-items: center; }
.ghost {
  border: 1px solid rgba(0,0,0,0.12); background: white;
  padding: 10px 14px; border-radius: 12px; cursor: pointer;
  font-size: 14px; color: rgba(0,0,0,0.55);
}
</style>

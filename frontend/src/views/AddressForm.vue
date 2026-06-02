<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
import RegionCascader from '../components/RegionCascader.vue'

const route = useRoute()
const router = useRouter()
const isEdit = !!route.params.id

const form = ref({
  receiverName: '', receiverPhone: '',
  province: '', city: '', district: '',
  detailAddress: '', isDefault: false,
})
const error = ref('')
const loading = ref(false)

onMounted(async () => {
  if (!isEdit) return
  try {
    const addrs = await api('/api/users/addresses') || []
    const addr = addrs.find(a => a.id === Number(route.params.id))
    if (addr) {
      form.value = {
        receiverName: addr.receiverName, receiverPhone: addr.receiverPhone,
        province: addr.province, city: addr.city, district: addr.district,
        detailAddress: addr.detailAddress, isDefault: addr.isDefault,
      }
    }
  } catch { /* ignore */ }
})

async function submit() {
  error.value = ''
  const { receiverName, receiverPhone, province, city, district, detailAddress } = form.value
  if (!receiverName || !receiverPhone || !province || !city || !district || !detailAddress) {
    error.value = '请填写完整信息'
    return
  }
  loading.value = true
  try {
    if (isEdit) {
      await api(`/api/users/addresses/${route.params.id}`, { method: 'PUT', body: form.value })
    } else {
      await api('/api/users/addresses', { method: 'POST', body: form.value })
    }
    router.replace('/profile')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h2>{{ isEdit ? '编辑地址' : '新增地址' }}</h2>
    <div class="card">
      <div class="form">
        <label>收货人 <input v-model="form.receiverName" placeholder="姓名" /></label>
        <label>手机号 <input v-model="form.receiverPhone" placeholder="手机号" /></label>
        <label>所在地区</label>
        <RegionCascader
          v-model:province="form.province"
          v-model:city="form.city"
          v-model:district="form.district"
          :province="form.province"
          :city="form.city"
          :district="form.district"
        />
        <label>详细地址 <input v-model="form.detailAddress" placeholder="街道/小区/门牌号" /></label>
        <label class="check">
          <input type="checkbox" v-model="form.isDefault" />
          设为默认地址
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="btn primary" :disabled="loading" @click="submit">
          {{ loading ? '保存中...' : '保存' }}
        </button>
        <button class="btn" @click="router.back()">取消</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 460px; margin: 0 auto; }
h2 { margin: 0 0 16px; font-size: 20px; }
.card { border: 1px solid rgba(0,0,0,0.08); border-radius: 16px; padding: 18px; background: white; }
.form { display: grid; gap: 10px; }
label { display: grid; gap: 4px; font-size: 13px; color: rgba(0,0,0,0.7); }
input { padding: 9px 10px; border: 1px solid rgba(0,0,0,0.12); border-radius: 10px; outline: none; }
.check { display: flex; flex-direction: row; align-items: center; gap: 8px; }
.check input { width: 16px; height: 16px; }
.btn {
  padding: 9px 14px; border: 1px solid rgba(0,0,0,0.12); border-radius: 10px;
  background: white; cursor: pointer; font-size: 14px;
}
.btn.primary { background: black; color: white; border-color: black; }
.btn:disabled { opacity: 0.5; }
.error { color: #b00020; font-size: 13px; margin: 0; }
</style>

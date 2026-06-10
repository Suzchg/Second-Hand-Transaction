<script setup>
import { ref, onMounted, computed } from 'vue'
import { api } from '../api.js'

const props = defineProps({
  province: { type: String, default: '' },
  city: { type: String, default: '' },
  district: { type: String, default: '' },
})
const emit = defineEmits(['update:province', 'update:city', 'update:district'])

const regions = ref([])
const selectedProvince = ref('')
const selectedCity = ref('')
const selectedDistrict = ref('')
const manualDistrict = ref('')  // 区县手动输入

const cities = computed(() => {
  const p = regions.value.find(r => r.name === selectedProvince.value)
  return p?.children || []
})
const districts = computed(() => {
  const c = cities.value.find(c => c.name === selectedCity.value || c.code === selectedCity.value)
  return c?.children || []
})
// 当前选中的城市是否有区县子级数据
const hasDistricts = computed(() => districts.value.length > 0)

onMounted(async () => {
  try {
    regions.value = await api('/api/regions', { auth: false }) || []
  } catch { /* ignore */ }
  if (props.province) selectedProvince.value = props.province
  if (props.city) selectedCity.value = props.city
  if (props.district) {
    selectedDistrict.value = props.district
    manualDistrict.value = props.district
  }
})

function onProvince(e) {
  selectedProvince.value = e.target.value
  selectedCity.value = ''
  selectedDistrict.value = ''
  manualDistrict.value = ''
  emit('update:province', selectedProvince.value)
  emit('update:city', '')
  emit('update:district', '')
}
function onCity(e) {
  selectedCity.value = e.target.value
  selectedDistrict.value = ''
  manualDistrict.value = ''
  emit('update:city', selectedCity.value)
  emit('update:district', '')
  // 延迟检查：如果该城市只有一个区县，自动选中
  setTimeout(() => {
    if (districts.value.length === 1) {
      selectedDistrict.value = districts.value[0].name
      manualDistrict.value = districts.value[0].name
      emit('update:district', districts.value[0].name)
    }
  }, 0)
}
function onDistrict(e) {
  selectedDistrict.value = e.target.value
  emit('update:district', selectedDistrict.value)
}
function onManualDistrict(e) {
  manualDistrict.value = e.target.value
  emit('update:district', manualDistrict.value)
}
</script>

<template>
  <div class="cascader">
    <select class="sel" :value="selectedProvince" @change="onProvince">
      <option value="">请选择省份</option>
      <option v-for="r in regions" :key="r.code" :value="r.name">{{ r.name }}</option>
    </select>
    <select class="sel" :value="selectedCity" @change="onCity" :disabled="!selectedProvince">
      <option value="">请选择城市</option>
      <option v-for="c in cities" :key="c.code || c.name" :value="c.name">{{ c.name }}</option>
    </select>
    <!-- 有区县数据：下拉选择 -->
    <select v-if="hasDistricts" class="sel" :value="selectedDistrict" @change="onDistrict" :disabled="!selectedCity">
      <option value="">请选择区县</option>
      <option v-for="d in districts" :key="d.code || d.name" :value="d.name">{{ d.name }}</option>
    </select>
    <!-- 无区县数据：手动输入 -->
    <input
      v-else
      class="sel inputLike"
      :value="manualDistrict"
      @input="onManualDistrict"
      placeholder="请输入区/县（如：长安区）"
      :disabled="!selectedCity"
    />
  </div>
</template>

<style scoped>
.cascader {
  display: grid;
  gap: 8px;
}
.sel, .inputLike {
  padding: 9px 10px;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  background: var(--bg-primary);
  color: var(--text-primary);
}
.sel:disabled, .inputLike:disabled { opacity: 0.5; }
</style>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['select', 'close'])
const addresses = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    addresses.value = await api('/api/users/addresses') || []
  } catch { /* ignore */ }
  loading.value = false
})

function select(addr) {
  emit('select', {
    receiverName: addr.receiverName,
    receiverPhone: addr.receiverPhone,
    receiverAddress: addr.fullAddress,
    addressId: addr.id,
  })
}
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <div class="panel">
      <h3>选择收货地址</h3>
      <p v-if="loading" class="muted">加载中...</p>
      <p v-else-if="!addresses.length" class="muted">
        暂无保存的地址，请先到<a href="/profile" style="color:inherit">个人中心</a>添加
      </p>
      <div v-else class="list">
        <div
          v-for="addr in addresses" :key="addr.id"
          class="item"
          @click="select(addr)"
        >
          <div class="row">
            <strong>{{ addr.receiverName }}</strong>
            <span>{{ addr.receiverPhone }}</span>
            <span v-if="addr.isDefault" class="tag defaultTag">默认</span>
            <span v-if="addr.tag && !addr.isDefault" class="tag labelTag">{{ addr.tag }}</span>
          </div>
          <div class="addr">{{ addr.fullAddress }}</div>
        </div>
      </div>
      <button class="btn" @click="emit('close')">关闭</button>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 100;
}
.panel {
  background: var(--bg-primary); border-radius: 16px; padding: 22px;
  width: 420px; max-width: 90vw; max-height: 80vh; overflow-y: auto;
  border: 1px solid var(--border-light);
}
h3 { margin: 0 0 12px; font-size: 16px; color: var(--text-primary); }
.muted { color: var(--text-tertiary); font-size: 13px; }
.list { display: grid; gap: 8px; }
.item {
  padding: 12px; border: 1px solid var(--border-light); border-radius: 12px;
  cursor: pointer; transition: border-color 0.12s;
}
.item:hover { border-color: var(--border-strong); }
.row { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-primary); }
.tag {
  font-size: 11px; padding: 1px 6px; border-radius: 4px;
  background: var(--bg-secondary); color: var(--text-secondary);
}
.defaultTag { background: #fff3e0; color: #e65100; font-weight: 500; }
.labelTag { background: #e3f2fd; color: #1565c0; }
.addr { margin-top: 4px; font-size: 12px; color: var(--text-secondary); }
.btn {
  margin-top: 12px; width: 100%; padding: 8px;
  border: 1px solid var(--border-default); border-radius: 10px;
  background: var(--bg-primary); color: var(--text-primary); cursor: pointer;
}
</style>

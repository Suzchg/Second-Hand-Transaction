<script setup>
import { ref } from 'vue'

const toasts = ref([])
let id = 0

function show(message, type = 'info', duration = 2500) {
  const toast = { id: ++id, message, type, duration }
  toasts.value.push(toast)
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== toast.id)
  }, duration)
  return toast.id
}

function dismiss(toastId) {
  toasts.value = toasts.value.filter(t => t.id !== toastId)
}

defineExpose({ show, dismiss })
</script>

<template>
  <Teleport to="body">
    <div class="toastContainer">
      <div
        v-for="t in toasts" :key="t.id"
        :class="['toast', t.type]"
        @click="dismiss(t.id)"
      >{{ t.message }}</div>
    </div>
  </Teleport>
</template>

<style scoped>
.toastContainer {
  position: fixed; top: 20px; left: 50%; transform: translateX(-50%);
  z-index: 9999; display: grid; gap: 8px; width: 360px; max-width: 90vw;
  pointer-events: none;
}
.toast {
  padding: 12px 18px; border-radius: var(--radius-md); font-size: 14px; text-align: center;
  color: white; cursor: pointer; pointer-events: auto;
  animation: toastIn 0.25s ease;
  box-shadow: var(--shadow-lg);
}
.toast.info { background: var(--text-primary); }
.toast.success { background: var(--success); }
.toast.error { background: var(--error); }
.toast.warn { background: var(--warning); }
@keyframes toastIn {
  from { opacity: 0; transform: translateY(-12px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>

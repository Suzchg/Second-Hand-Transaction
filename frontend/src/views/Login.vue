<script setup>
import { ref } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'

const router = useRouter()
const mode = ref('login')           // login | register
const identityType = ref('PHONE')   // PHONE | EMAIL
const identifier = ref('')          // 手机号或邮箱值
const password = ref('')
const error = ref('')
const fieldErrors = ref({})
const loading = ref(false)

/** 简单的格式校验 */
function validate() {
  fieldErrors.value = {}
  const val = identifier.value.trim()

  if (!val) {
    fieldErrors.value.identifier = '请输入手机号或邮箱'
    return false
  }

  if (identityType.value === 'PHONE') {
    if (!/^1[3-9]\d{9}$/.test(val)) {
      fieldErrors.value.identifier = '请输入正确的手机号'
      return false
    }
  } else {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)) {
      fieldErrors.value.identifier = '请输入正确的邮箱地址'
      return false
    }
  }

  if (password.value.length < 6) {
    fieldErrors.value.password = '密码至少 6 位'
    return false
  }

  return true
}

async function submit() {
  error.value = ''
  fieldErrors.value = {}

  if (!validate()) return

  loading.value = true
  try {
    const body = {
      identityType: identityType.value,
      identifier: identifier.value.trim(),
      password: password.value,
    }

    const path = mode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    // api() 已自动解包 ApiResponse.data，直接拿到 { accessToken, userId, nickname }
    const user = await api(path, { method: 'POST', body, auth: false })

    localStorage.setItem('token', user.accessToken)
    localStorage.setItem('userId', String(user.userId))
    localStorage.setItem('nickname', user.nickname || '新用户')
    localStorage.setItem('role', user.role || 'USER')
    localStorage.setItem('lastIdentifier', identifier.value.trim())

    // 强制整页刷新，确保 App.vue onMounted 正确读取登录态
    window.location.replace('/')
  } catch (e) {
    error.value = e.message || '操作失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="card">
    <h1 class="title">SecondHand</h1>
    <p class="sub">登录后开始浏览与交易</p>

    <!-- 登录/注册切换 -->
    <div class="seg">
      <button
        class="segBtn" :class="{ active: mode === 'login' }"
        @click="mode = 'login'; error = ''; fieldErrors = {}"
      >登录</button>
      <button
        class="segBtn" :class="{ active: mode === 'register' }"
        @click="mode = 'register'; error = ''; fieldErrors = {}"
      >注册</button>
    </div>

    <!-- 手机号/邮箱切换 -->
    <div class="seg" style="margin-top: 10px">
      <button
        class="segBtn" :class="{ active: identityType === 'PHONE' }"
        @click="identityType = 'PHONE'; identifier = ''; error = ''; fieldErrors = {}"
      >手机号</button>
      <button
        class="segBtn" :class="{ active: identityType === 'EMAIL' }"
        @click="identityType = 'EMAIL'; identifier = ''; error = ''; fieldErrors = {}"
      >邮箱</button>
    </div>

    <div class="form">
      <!-- 标识符输入 -->
      <label>
        {{ identityType === 'PHONE' ? '手机号' : '邮箱' }}
        <input
          v-model="identifier"
          :placeholder="identityType === 'PHONE' ? '例如：13800138000' : '例如：me@example.com'"
          autocomplete="username"
          @input="fieldErrors.identifier = ''"
        />
        <span v-if="fieldErrors.identifier" class="fieldError">{{ fieldErrors.identifier }}</span>
      </label>

      <!-- 密码输入 -->
      <label>
        密码
        <input
          v-model="password"
          type="password"
          placeholder="至少 6 位"
          autocomplete="current-password"
          @input="fieldErrors.password = ''"
        />
        <span v-if="fieldErrors.password" class="fieldError">{{ fieldErrors.password }}</span>
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button class="primary" :disabled="loading" @click="submit">
        {{ loading ? '处理中...' : mode === 'login' ? '登录' : '注册' }}
      </button>

      <p class="hint">
        {{ mode === 'login' ? '还没有账号？切换至「注册」' : '已有账号？切换至「登录」' }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.card {
  max-width: 420px;
  margin: 60px auto 0;
  padding: 28px 24px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 18px;
  background: white;
}
.title {
  margin: 0;
  font-size: 24px;
  letter-spacing: 0.2px;
  text-align: center;
}
.sub {
  margin: 8px 0 18px;
  color: rgba(0, 0, 0, 0.5);
  font-size: 14px;
  text-align: center;
}
.seg {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.segBtn {
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: white;
  padding: 9px 10px;
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s;
}
.segBtn.active {
  border-color: rgba(0, 0, 0, 0.4);
  background: rgba(0, 0, 0, 0.06);
  font-weight: 600;
}
.form {
  margin-top: 16px;
  display: grid;
  gap: 12px;
}
label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.7);
}
input {
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  outline: none;
  font-size: 15px;
  transition: border-color 0.15s;
}
input:focus {
  border-color: rgba(0, 0, 0, 0.35);
}
.fieldError {
  font-size: 12px;
  color: #b00020;
}
.primary {
  border: 0;
  background: black;
  color: white;
  padding: 11px 12px;
  border-radius: 12px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  transition: opacity 0.15s;
}
.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.error {
  margin: 0;
  padding: 10px 12px;
  background: #fce4ec;
  border-radius: 10px;
  color: #b00020;
  font-size: 13px;
}
.hint {
  text-align: center;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  margin: 0;
}
</style>

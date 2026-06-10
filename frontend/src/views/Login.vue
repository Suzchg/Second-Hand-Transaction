<script setup>
import { ref } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user.js'

const router = useRouter()
const mode = ref('login')
const identityType = ref('PHONE')
const identifier = ref('')
const password = ref('')
const error = ref('')
const fieldErrors = ref({})
const loading = ref(false)

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
    const user = await api(path, { method: 'POST', body, auth: false })

    localStorage.setItem('lastIdentifier', identifier.value.trim())
    const userStore = useUserStore()
    userStore.setAuth(user)

    window.location.replace('/')
  } catch (e) {
    error.value = e.message || '操作失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="loginPage">
    <!-- 背景装饰 -->
    <div class="bgDecor" />

    <div class="loginCard">
      <div class="cardHeader">
        <span class="logoEmoji"><AppIcon name="users" :size="40"/></span>
        <h1 class="logoTitle">Second hand</h1>
        <p class="logoDesc">Your marketplace for pre-loved treasures</p>
      </div>

      <!-- 登录/注册切换 -->
      <div class="segRow">
        <button
          :class="['segBtn', { active: mode === 'login' }]"
          @click="mode = 'login'; error = ''; fieldErrors = {}"
        >登录</button>
        <button
          :class="['segBtn', { active: mode === 'register' }]"
          @click="mode = 'register'; error = ''; fieldErrors = {}"
        >注册</button>
      </div>

      <!-- 手机号/邮箱切换 -->
      <div class="segRow" style="margin-top: 8px">
        <button
          :class="['segBtn', { active: identityType === 'PHONE' }]"
          @click="identityType = 'PHONE'; identifier = ''; error = ''; fieldErrors = {}"
        ><AppIcon name="phone" :size="16"/> 手机号</button>
        <button
          :class="['segBtn', { active: identityType === 'EMAIL' }]"
          @click="identityType = 'EMAIL'; identifier = ''; error = ''; fieldErrors = {}"
        ><AppIcon name="at-sign" :size="16"/> 邮箱</button>
      </div>

      <div class="formArea">
        <label>
          {{ identityType === 'PHONE' ? '手机号' : '邮箱地址' }}
          <input
            v-model="identifier"
            :placeholder="identityType === 'PHONE' ? '例如：13800138000' : '例如：me@example.com'"
            autocomplete="username"
            @input="fieldErrors.identifier = ''"
          />
          <span v-if="fieldErrors.identifier" class="fieldErr">{{ fieldErrors.identifier }}</span>
        </label>

        <label>
          密码
          <input
            v-model="password"
            type="password"
            placeholder="至少 6 位字符"
            autocomplete="current-password"
            @input="fieldErrors.password = ''"
          />
          <span v-if="fieldErrors.password" class="fieldErr">{{ fieldErrors.password }}</span>
        </label>

        <div v-if="error" class="errorBanner">{{ error }}</div>

        <button class="submitBtn" :disabled="loading" @click="submit">
          {{ loading ? '处理中...' : mode === 'login' ? '登录' : '注册新账号' }}
        </button>

        <p class="switchHint">
          {{ mode === 'login' ? '还没有账号？切换至「注册」创建新账号' : '已有账号？切换至「登录」' }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.loginPage {
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.bgDecor {
  position: absolute;
  top: -120px;
  left: 50%;
  transform: translateX(-50%);
  width: 500px;
  height: 500px;
  background: var(--brand-gradient-soft);
  border-radius: 50%;
  opacity: 0.4;
  pointer-events: none;
}

.loginCard {
  position: relative;
  width: 100%;
  max-width: 420px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: var(--space-3xl) var(--space-2xl);
  box-shadow: var(--shadow-lg);
}

.cardHeader {
  text-align: center;
  margin-bottom: var(--space-xl);
}

.logoEmoji { display: flex; justify-content: center; margin-bottom: var(--space-sm); color: var(--brand); }

.logoTitle {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  white-space: nowrap;
  letter-spacing: 2px;
  font-style: italic;
}

.logoDesc {
  margin: var(--space-sm) 0 0;
  font-size: 13px;
  color: var(--text-tertiary);
}

/* 切换按钮 */
.segRow {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-sm);
}

.segBtn {
  border: 1.5px solid var(--border-default);
  background: var(--bg-primary);
  padding: 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.segBtn:hover { border-color: var(--border-strong); }

.segBtn.active {
  border-color: var(--brand-dark);
  background: var(--brand-light);
  color: var(--brand-darker);
  font-weight: 600;
}

/* 表单 */
.formArea {
  margin-top: var(--space-xl);
  display: grid;
  gap: var(--space-md);
}

.formArea label {
  display: grid;
  gap: var(--space-xs);
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.formArea input {
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  font-size: 15px;
  color: var(--text-primary);
  background: var(--bg-tertiary);
  transition: all var(--transition-fast);
}

.formArea input:focus {
  background: var(--bg-primary);
  border-color: var(--brand-dark) !important;
  box-shadow: 0 0 0 3px rgba(14, 181, 166, 0.12) !important;
}

.fieldErr {
  font-size: 12px;
  color: var(--error);
}

.errorBanner {
  padding: 10px 14px;
  background: var(--error-bg);
  border: 1px solid var(--error-border);
  border-radius: var(--radius-md);
  color: var(--error);
  font-size: 13px;
}

.submitBtn {
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 13px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 16px;
  font-weight: 700;
  box-shadow: var(--shadow-brand);
  transition: all var(--transition-fast);
}

.submitBtn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(14, 181, 166, 0.4);
}

.submitBtn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.switchHint {
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
}

@media (max-width: 480px) {
  .loginCard {
    padding: var(--space-xl) var(--space-lg);
    border-radius: var(--radius-lg);
  }
}
</style>

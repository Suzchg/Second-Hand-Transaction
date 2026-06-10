import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api.js'

/**
 * 用户认证与资料 Store。
 * 集中管理 token、用户信息，替代散落的 localStorage 直接读写。
 */
export const useUserStore = defineStore('user', () => {
  // ---- state ----
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const role = ref(localStorage.getItem('role') || 'USER')
  const avatarUrl = ref(localStorage.getItem('avatarUrl') || '')

  // ---- getters ----
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  // ---- actions ----
  function setAuth(authData) {
    token.value = authData.accessToken
    userId.value = String(authData.userId)
    nickname.value = authData.nickname || '新用户'
    role.value = authData.role || 'USER'
    avatarUrl.value = authData.avatarUrl || ''

    localStorage.setItem('token', token.value)
    localStorage.setItem('userId', userId.value)
    localStorage.setItem('nickname', nickname.value)
    localStorage.setItem('role', role.value)
    localStorage.setItem('avatarUrl', avatarUrl.value)
  }

  async function fetchProfile() {
    if (!token.value) return
    try {
      const user = await api('/api/auth/me')
      nickname.value = user.nickname || '用户'
      avatarUrl.value = user.avatarUrl || ''
      role.value = user.role || 'USER'
      userId.value = String(user.userId || '')

      localStorage.setItem('nickname', nickname.value)
      localStorage.setItem('avatarUrl', avatarUrl.value)
      localStorage.setItem('role', role.value)
      localStorage.setItem('userId', userId.value)

      // 同步到多账号列表
      saveToAccountList(user)
    } catch {
      clearAuth()
    }
  }

  function saveToAccountList(user) {
    try {
      const list = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
      const idx = list.findIndex(a => a.userId === user.userId)
      const acc = {
        userId: user.userId,
        nickname: user.nickname || '用户',
        role: user.role || 'USER',
        token: token.value,
        identifier: localStorage.getItem('lastIdentifier') || '',
        avatarUrl: user.avatarUrl || '',
      }
      if (idx >= 0) {
        acc.identifier = list[idx].identifier || acc.identifier
        list[idx] = acc
      } else {
        list.push(acc)
      }
      localStorage.setItem('savedAccounts', JSON.stringify(list))
    } catch { /* ignore */ }
  }

  function updateAvatar(url) {
    avatarUrl.value = url
    localStorage.setItem('avatarUrl', url)
  }

  function updateNickname(name) {
    nickname.value = name
    localStorage.setItem('nickname', name)
  }

  function clearAuth() {
    token.value = ''
    userId.value = ''
    nickname.value = ''
    role.value = 'USER'
    avatarUrl.value = ''

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('role')
    localStorage.removeItem('avatarUrl')

    // 清除已保存账号列表中的 token
    try {
      const list = JSON.parse(localStorage.getItem('savedAccounts') || '[]')
      list.forEach(a => { a.token = '' })
      localStorage.setItem('savedAccounts', JSON.stringify(list))
    } catch { /* ignore */ }
  }

  function logout() {
    clearAuth()
  }

  return {
    token, userId, nickname, role, avatarUrl,
    isLoggedIn, isAdmin,
    setAuth, fetchProfile, updateAvatar, updateNickname, clearAuth, logout,
  }
})

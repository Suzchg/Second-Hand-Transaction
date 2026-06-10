import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

/**
 * 主题 Store。
 * 管理亮色/暗色模式切换，持久化到 localStorage，并同步 data-theme 属性到 <html>。
 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(false)

  // ---- 初始化 ----
  function init() {
    const saved = localStorage.getItem('theme')
    if (saved === 'dark') {
      isDark.value = true
    } else if (saved === 'light') {
      isDark.value = false
    } else {
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    apply()
  }

  function apply() {
    document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : 'light')
  }

  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
    apply()
  }

  // 监听系统主题变化（仅当用户未手动设置时）
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      if (!localStorage.getItem('theme')) {
        isDark.value = e.matches
        apply()
      }
    })
  }

  return { isDark, init, toggle }
})

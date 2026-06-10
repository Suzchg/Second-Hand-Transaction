/**
 * 背景图懒加载指令 / composable。
 *
 * 原理：使用 IntersectionObserver 监听元素进入视口，
 * 进入时才设置 background-image，并添加淡入动画。
 */

const imageCache = new Set()

function loadBgImage(el, url) {
  if (!url) return
  // 用 Image 对象预加载，确保图片就绪后再设置背景
  const img = new Image()
  img.onload = () => {
    el.style.backgroundImage = `url(${url})`
    el.classList.add('bg-loaded')
  }
  img.onerror = () => {
    // 加载失败也设置，让浏览器显示默认占位
    el.style.backgroundImage = `url(${url})`
  }
  img.src = url
}

export function createLazyBgObserver() {
  if (typeof IntersectionObserver === 'undefined') {
    // 不支持 IntersectionObserver 的浏览器直接加载
    return {
      observe(el, url) { loadBgImage(el, url) },
      unobserve() {},
      disconnect() {},
    }
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const el = entry.target
          const url = el.dataset.lazyBg
          if (url) {
            loadBgImage(el, url)
            el.removeAttribute('data-lazy-bg')
          }
          observer.unobserve(el)
        }
      })
    },
    {
      // 提前 200px 开始加载（用户快滚动到时已经加载好了）
      rootMargin: '200px',
      threshold: 0,
    }
  )

  return observer
}

/**
 * Vue 自定义指令：v-lazy-bg
 * 用法：<div v-lazy-bg="coverUrl" class="cardCover" />
 */
export const vLazyBg = {
  mounted(el, binding) {
    const url = binding.value
    if (!url) return
    el.dataset.lazyBg = url
    // 使用全局单例 observer
    if (!vLazyBg._observer) {
      vLazyBg._observer = createLazyBgObserver()
    }
    vLazyBg._observer.observe(el)
  },
  updated(el, binding) {
    const url = binding.value
    if (url && url !== el.dataset.lazyBg) {
      // URL 变化了，重新设置
      el.dataset.lazyBg = url
      if (vLazyBg._observer) vLazyBg._observer.unobserve(el)
      if (vLazyBg._observer) vLazyBg._observer.observe(el)
    }
  },
  unmounted(el) {
    if (vLazyBg._observer) vLazyBg._observer.unobserve(el)
  },
}

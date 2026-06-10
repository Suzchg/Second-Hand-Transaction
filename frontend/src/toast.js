// Simple global toast — set by App.vue on mount
let _show = () => {}

export const toast = {
  setup(fn) { _show = fn },
  show(msg, type = 'info') { _show(msg, type) },
  success(msg) { _show(msg, 'success') },
  error(msg) { _show(msg, 'error') },
  warn(msg) { _show(msg, 'warn') },
}

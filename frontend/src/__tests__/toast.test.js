import { describe, it, expect, vi } from 'vitest'
import { toast } from '../toast.js'

describe('toast', () => {
  it('show 调用注册的回调', () => {
    const fn = vi.fn()
    toast.setup(fn)

    toast.show('hello', 'info')

    expect(fn).toHaveBeenCalledWith('hello', 'info')
  })

  it('success/error/warn 传递对应类型', () => {
    const fn = vi.fn()
    toast.setup(fn)

    toast.success('ok')
    toast.error('bad')
    toast.warn('careful')

    expect(fn).toHaveBeenNthCalledWith(1, 'ok', 'success')
    expect(fn).toHaveBeenNthCalledWith(2, 'bad', 'error')
    expect(fn).toHaveBeenNthCalledWith(3, 'careful', 'warn')
  })
})

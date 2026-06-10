import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppIcon from '../components/AppIcon.vue'

describe('AppIcon', () => {
  const iconNames = [
    'cart', 'close', 'sun', 'moon', 'chat', 'package', 'clipboard',
    'settings', 'home', 'folder', 'sparkle', 'image', 'mail', 'users',
    'phone', 'at-sign', 'truck', 'file-text', 'heart', 'message-square',
    'star', 'dollar', 'alert-octagon', 'user', 'edit', 'refresh', 'logout',
    'bar-chart', 'tool', 'rocket', 'check', 'map-pin', 'shield',
    'alert-triangle', 'shopping-bag', 'send', 'thumbs-up', 'plus',
    'monitor', 'camera', 'zap', 'activity', 'book', 'music', 'gamepad',
  ]

  it('should render all icon names without error', () => {
    for (const name of iconNames) {
      const wrapper = mount(AppIcon, { props: { name } })
      const svg = wrapper.find('svg')
      expect(svg.exists()).toBe(true)
      expect(svg.element.tagName).toBe('svg')
    }
  })

  it('should apply active class when active prop is true', () => {
    const wrapper = mount(AppIcon, { props: { name: 'heart', active: true } })
    expect(wrapper.find('svg').classes()).toContain('active')
  })

  it('should not have active class by default', () => {
    const wrapper = mount(AppIcon, { props: { name: 'heart' } })
    expect(wrapper.find('svg').classes()).not.toContain('active')
  })

  it('should use custom size prop', () => {
    const wrapper = mount(AppIcon, { props: { name: 'cart', size: 32 } })
    expect(wrapper.find('svg').attributes('width')).toBe('32')
    expect(wrapper.find('svg').attributes('height')).toBe('32')
  })

  it('should default to size 20', () => {
    const wrapper = mount(AppIcon, { props: { name: 'cart' } })
    expect(wrapper.find('svg').attributes('width')).toBe('20')
  })
})

import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import App from '../App.vue'

describe('App', () => {
  it('앱 제목과 Element Plus 버튼을 보여준다', () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('CRM pos')
    expect(wrapper.find('.el-button--primary').text()).toBe('저장')
  })
})

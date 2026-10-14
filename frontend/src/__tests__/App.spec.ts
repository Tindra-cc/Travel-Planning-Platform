import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../App.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }]
})

describe('App', () => {
  it('未登录时展示登录入口', async () => {
    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
        stubs: { RouterView: true }
      }
    })
    expect(wrapper.text()).toContain('登录')
  })
})

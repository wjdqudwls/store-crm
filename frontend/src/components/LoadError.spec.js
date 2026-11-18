import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import LoadError from './LoadError.vue'
import ProductsView from '@/views/ProductsView.vue'

vi.mock('@/api/products', () => ({
  getProducts: vi.fn(),
  importProducts: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
import { getProducts } from '@/api/products'

const global = { plugins: [ElementPlus] }

describe('LoadError 띠 (F12)', () => {
  it('should_숨김_when_오류가_없음', () => {
    expect(
      mount(LoadError, { global, props: { error: null } })
        .find('[role=alert]')
        .exists(),
    ).toBe(false)
  })

  it('should_메시지와_다시_시도_when_오류가_있음', async () => {
    const w = mount(LoadError, { global, props: { error: { message: '서버 오류', status: 500 } } })
    expect(w.text()).toContain('불러오지 못했습니다')
    expect(w.text()).toContain('서버 오류')
    await w.find('button').trigger('click')
    expect(w.emitted('retry')).toHaveLength(1)
  })
})

describe('화면을 열 때 API가 실패하면 (상품 목록 예)', () => {
  it('should_스켈레톤을_멈추고_띠를_보임_when_500', async () => {
    getProducts.mockRejectedValueOnce({
      status: 500,
      body: { code: 'INTERNAL_ERROR', message: '일시적인 오류입니다' },
    })
    const w = mount(ProductsView, { global })
    await flushPromises()
    expect(w.find('.el-skeleton').exists()).toBe(false)
    expect(w.find('[role=alert]').text()).toContain('일시적인 오류입니다')
  })

  it('should_다시_불러옴_when_다시_시도를_누르면', async () => {
    getProducts.mockRejectedValueOnce({
      status: 0,
      body: { code: 'NETWORK_ERROR', message: '연결 끊김' },
    })
    getProducts.mockResolvedValueOnce({ content: [] })
    const w = mount(ProductsView, { global })
    await flushPromises()
    await w.find('[role=alert] button').trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').exists()).toBe(false)
    expect(w.text()).toContain('등록한 상품이 없습니다')
  })
})

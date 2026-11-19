import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// 실서버 모드(VITE_API_MODE=real)에서 B1 함수가 03의 주소·본문으로 http를 부르는지 (F2·F4 연결)
const http = { get: vi.fn(), post: vi.fn(), patch: vi.fn() }
vi.mock('./http', () => ({ http, notConnected: (n) => Promise.reject(new Error(n)) }))

describe('실서버 연결: 고객 (03 3.2·4.3·4.3a·4.4)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    api = await import('./customers')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_POST_customers_when_직원_대리_등록', async () => {
    await api.createCustomer({ name: '김하늘' })
    expect(http.post).toHaveBeenCalledWith('/customers', { name: '김하늘' })
  })

  it('should_이름_page_size를_쿼리로_when_검색', async () => {
    await api.searchCustomers('김')
    expect(http.get).toHaveBeenCalledWith('/customers', {
      params: { name: '김', page: 0, size: 20 },
    })
  })

  it('should_include_address를_보냄_when_상세주소가_필요한_화면', async () => {
    await api.getCustomer(1024, { includeAddress: true })
    expect(http.get).toHaveBeenCalledWith('/customers/1024', { params: { include: 'address' } })
    await api.getCustomer(1024)
    expect(http.get).toHaveBeenLastCalledWith('/customers/1024', { params: {} })
  })

  it('should_PATCH_when_고객_수정', async () => {
    await api.updateCustomer(7, { phone: '010-1' })
    expect(http.patch).toHaveBeenCalledWith('/customers/7', { phone: '010-1' })
  })
})

describe('실서버 연결: 상품 (03 3.4)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    api = await import('./products')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_상품_상세_등록_수정_주소', async () => {
    await api.getProduct(3)
    expect(http.get).toHaveBeenCalledWith('/products/3')
    await api.createProduct({ name: '코트' })
    expect(http.post).toHaveBeenCalledWith('/products', { name: '코트' })
    await api.updateProduct(3, { name: '코트2' })
    expect(http.patch).toHaveBeenCalledWith('/products/3', { name: '코트2' })
  })
})

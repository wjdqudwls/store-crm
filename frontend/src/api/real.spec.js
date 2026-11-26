import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// 실서버 모드(VITE_API_MODE=real)에서 B1 함수가 03의 주소·본문으로 http를 부르는지 (F2·F4 연결)
const http = { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() }
vi.mock('./http', () => ({ http, notConnected: (n) => Promise.reject(new Error(n)) }))

describe('실서버 연결: 고객 (03 3.2·4.3·4.3a·4.4)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    http.delete = vi.fn().mockResolvedValue({})
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

  it('should_구매이력_주소와_검색어_when_고객_상세_구매_탭', async () => {
    await api.getPurchases(5, '코트')
    expect(http.get).toHaveBeenCalledWith('/customers/5/purchases', {
      params: { keyword: '코트', size: 100 },
    })
  })

  it('should_메모_주소와_본문_when_목록_작성_수정_삭제', async () => {
    await api.getNotes(5)
    expect(http.get).toHaveBeenCalledWith('/customers/5/notes', { params: { size: 100 } })
    await api.createNote(5, { type: 'CONSULT', content: '메모', staffId: 4 })
    expect(http.post).toHaveBeenCalledWith('/customers/5/notes', {
      type: 'CONSULT',
      content: '메모',
      staffId: 4,
    })
    await api.updateNote(9, '고침')
    expect(http.patch).toHaveBeenCalledWith('/notes/9', { content: '고침' })
    await api.deleteNote(9)
    expect(http.delete).toHaveBeenCalledWith('/notes/9')
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

describe('실서버 연결: 판매·반품·마감 (03 4.1·4.2·4.9·4.10·4.12)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    http.delete = vi.fn().mockResolvedValue({})
    api = await import('./sales')
  })
  afterEach(() => vi.unstubAllEnvs())

  const KEY = '6b0f6c1e-2f7a-4f3c-9a51-1d2c3e4f5a6b'

  it('should_Idempotency-Key_헤더로_보냄_when_판매_반품_기록취소', async () => {
    await api.createSale({ sellerStaffId: 4, lines: [] }, KEY)
    expect(http.post).toHaveBeenLastCalledWith(
      '/sales',
      { sellerStaffId: 4, lines: [] },
      { headers: { 'Idempotency-Key': KEY } },
    )
    await api.returnSale(7, { staffId: 4, lines: [] }, KEY)
    expect(http.post).toHaveBeenLastCalledWith(
      '/sales/7/returns',
      { staffId: 4, lines: [] },
      {
        headers: { 'Idempotency-Key': KEY },
      },
    )
    await api.voidSale(7, { staffId: 4 }, KEY)
    expect(http.post).toHaveBeenLastCalledWith(
      '/sales/7/void',
      { staffId: 4 },
      {
        headers: { 'Idempotency-Key': KEY },
      },
    )
  })

  it('should_PUT_sales_id_본문과_키_when_정정', async () => {
    const body = { staffId: 4, customerId: 5, lines: [{ skuId: 2, qty: 1, lineAmount: 1000 }] }
    await api.correctSale(7, body, KEY)
    expect(http.put).toHaveBeenLastCalledWith('/sales/7', body, {
      headers: { 'Idempotency-Key': KEY },
    })
  })

  it('should_같은_키로_다시_보냄_when_두_번_눌러도(재시도)', async () => {
    await api.createSale({ sellerStaffId: 4, lines: [] }, KEY)
    await api.createSale({ sellerStaffId: 4, lines: [] }, KEY)
    const keys = http.post.mock.calls.map((c) => c[2].headers['Idempotency-Key'])
    expect(keys).toEqual([KEY, KEY])
  })

  it('should_첫_항목을_줌_when_daily-summary가_배열', async () => {
    http.get.mockResolvedValue([{ storeId: 1, saleCount: 3, closed: false, closedAt: null }])
    expect(await api.getDailySummary()).toEqual({
      storeId: 1,
      saleCount: 3,
      closed: false,
      closedAt: null,
    })
    expect(http.get).toHaveBeenCalledWith('/sales/daily-summary', { params: {} })
  })

  it('should_기간과_매장을_쿼리로_when_판매_내역', async () => {
    await api.getSales({ from: '2026-11-01', to: '2026-11-19', storeId: 1 })
    expect(http.get).toHaveBeenCalledWith('/sales', {
      params: { from: '2026-11-01', to: '2026-11-19', storeId: 1, size: 100 },
    })
  })

  it('should_마감_조회_다시열기_주소', async () => {
    await api.closeDay()
    expect(http.post).toHaveBeenCalledWith('/day-closes')
    await api.getDayCloses(2)
    expect(http.get).toHaveBeenCalledWith('/stores/2/day-closes')
    await api.reopenDay(2, '2026-11-19')
    expect(http.delete).toHaveBeenCalledWith('/stores/2/day-closes/2026-11-19')
  })
})

describe('실서버 연결: 매장·직원 목록 (03 3.7)', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue([]))
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_GET_stores와_stores_id_staff_when_본사_화면', async () => {
    const api = await import('./stores')
    await api.getStores()
    expect(http.get).toHaveBeenLastCalledWith('/stores')
    await api.getStoreAccounts(2)
    expect(http.get).toHaveBeenLastCalledWith('/stores/2/staff')
    await api.getStore(2)
    expect(http.get).toHaveBeenLastCalledWith('/stores/2')
  })

  it('should_내_매장_직원을_받음_when_판매_사원_고르기', async () => {
    const { createPinia, setActivePinia } = await import('pinia')
    setActivePinia(createPinia())
    const { useSessionStore } = await import('@/stores/session')
    useSessionStore().me = { id: 2, role: 'STORE', storeId: 1 }
    const { getStaff } = await import('./sales')
    await getStaff()
    expect(http.get).toHaveBeenLastCalledWith('/stores/1/staff')
  })
})

describe('실서버 연결: 후속 연락 할 일·단계 설정 (03 3.3·4.7)', () => {
  let customers
  let settings
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue([]))
    http.put = vi.fn().mockResolvedValue([])
    customers = await import('./customers')
    settings = await import('./settings')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_오늘_할_일과_고객_할_일_주소', async () => {
    await customers.getToday()
    expect(http.get).toHaveBeenLastCalledWith('/follow-ups/today', { params: { size: 100 } })
    await customers.getFollowUps(5)
    expect(http.get).toHaveBeenLastCalledWith('/customers/5/follow-ups', { params: { size: 100 } })
  })

  it('should_완료_건너뛰기_방문함_주소와_본문', async () => {
    const body = { staffId: 4, callResult: 'COMPLETED', channel: 'CALL', content: '통화' }
    await customers.completeFollowUp(7, body)
    expect(http.post).toHaveBeenLastCalledWith('/follow-ups/7/complete', body)
    await customers.skipFollowUp(7)
    expect(http.post).toHaveBeenLastCalledWith('/follow-ups/7/skip')
    await customers.completeVisit(7)
    expect(http.post).toHaveBeenLastCalledWith('/follow-ups/7/visited')
  })

  it('should_단계_설정_조회_저장_주소', async () => {
    await settings.getFollowUpRules()
    expect(http.get).toHaveBeenLastCalledWith('/follow-up-rules')
    const list = [
      { id: 1, name: '감사 인사', amount: 2, unit: 'DAY', promotional: false, enabled: true },
    ]
    await settings.saveFollowUpRules(list)
    expect(http.put).toHaveBeenCalledWith('/follow-up-rules', list)
  })
})

describe('실서버 연결: 콜리스트 (03 3.3)', () => {
  it('should_call-list_주소와_끈_기본값', async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    const { getCallList } = await import('./customers')
    await getCallList({ scope: 'MINE', minGrade: null, inactiveDays: null, promoOnly: false })
    expect(http.get).toHaveBeenLastCalledWith('/call-list', {
      params: { scope: 'MINE', minGrade: 'NORMAL', inactiveDays: 0, promoOnly: false, size: 100 },
    })
    vi.unstubAllEnvs()
  })
})

describe('실서버 연결: 알림함·알림 설정 (03 3.3, 4.12)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    http.put = vi.fn().mockResolvedValue({})
    api = await import('./notifications')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_알림함_조회와_읽음_주소', async () => {
    await api.getNotifications()
    expect(http.get).toHaveBeenLastCalledWith('/notifications', { params: { size: 100 } })
    await api.markRead(9001)
    expect(http.post).toHaveBeenLastCalledWith('/notifications/9001/read')
  })

  it('should_알림_시점만_PUT_when_설정_저장(푸시는_별도_구독_API)', async () => {
    await api.getNotifySetting()
    expect(http.get).toHaveBeenLastCalledWith('/me/notification-settings')
    await api.saveNotifySetting({ timing: 'BOTH', push: true })
    expect(http.put).toHaveBeenCalledWith('/me/notification-settings', { timing: 'BOTH' })
  })
})

describe('실서버 연결: QR·키오스크 등록 (03 4.5)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    api = await import('./public')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_join-info에_매장·직원_코드를_보냄', async () => {
    await api.getJoinInfo('GN01', 'S001')
    expect(http.get).toHaveBeenLastCalledWith('/public/join-info', {
      params: { store: 'GN01', staff: 'S001' },
    })
    await api.getJoinInfo('GN01')
    expect(http.get).toHaveBeenLastCalledWith('/public/join-info', { params: { store: 'GN01' } })
  })

  it('should_QR은_public_키오스크는_kiosk_주소로_등록', async () => {
    await api.publicRegister({ storeCode: 'GN01' })
    expect(http.post).toHaveBeenLastCalledWith('/public/registrations', { storeCode: 'GN01' })
    await api.kioskRegister({ name: 'x' })
    expect(http.post).toHaveBeenLastCalledWith('/kiosk/registrations', { name: 'x' })
  })
})

describe('실서버 연결: 상품 엑셀 (03 4.6)', () => {
  let api
  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    Object.values(http).forEach((f) => f.mockReset().mockResolvedValue({}))
    api = await import('./products')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('should_file_하나를_multipart로_보냄_when_올리기', async () => {
    const file = new File(['x'], 'a.xlsx')
    await api.importProducts(file)
    const [url, body, config] = http.post.mock.calls.at(-1)
    expect(url).toBe('/products/import')
    expect(body.get('file').name).toBe('a.xlsx')
    expect(config).toBeUndefined() // Content-Type은 axios가 boundary와 함께 정한다
  })

  it('should_blob으로_받음_when_폼_내려받기', async () => {
    await api.downloadImportTemplate()
    expect(http.get).toHaveBeenLastCalledWith('/products/import-template', { responseType: 'blob' })
  })
})

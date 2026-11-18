import { describe, it, expect } from 'vitest'
import { canChangeSale, canReturnSale, isOwnStoreSale } from './saleChange'

const sale = (storeId, soldAt = '2026-11-18T10:42:00+09:00') => ({ store: { id: storeId }, soldAt })
const ctx = (over = {}) => ({
  role: 'STORE',
  storeId: 1,
  todayClosed: false,
  today: '2026-11-18',
  ...over,
})

describe('다른 매장 영수증은 보기만 (05 SCR-03)', () => {
  it('자기 매장 판매만 자기 매장이다', () => {
    expect(isOwnStoreSale(sale(1), 1)).toBe(true)
    expect(isOwnStoreSale(sale(2), 1)).toBe(false)
  })
})

describe('판매를 고칠 수 있는지 (BR-16, W8)', () => {
  it('매장 계정은 자기 매장의 판매한 날·마감 전에만 고친다', () => {
    expect(canChangeSale(sale(1), ctx())).toBe(true)
  })

  it('지난 날 판매는 매장 계정이 고치지 못한다', () => {
    expect(canChangeSale(sale(1, '2026-11-17T10:42:00+09:00'), ctx())).toBe(false)
  })

  it('오늘이 마감되면 매장 계정이 고치지 못한다', () => {
    expect(canChangeSale(sale(1), ctx({ todayClosed: true }))).toBe(false)
  })

  it('다른 매장 판매는 매장 계정이 고치지 못한다', () => {
    expect(canChangeSale(sale(2), ctx())).toBe(false)
  })

  it('본사는 언제나 고친다', () => {
    expect(
      canChangeSale(
        sale(2, '2026-01-02T10:00:00+09:00'),
        ctx({ role: 'HQ', storeId: null, todayClosed: true }),
      ),
    ).toBe(true)
  })

  it('직원 개인 계정은 고치지 못한다', () => {
    expect(canChangeSale(sale(1), ctx({ role: 'SENIOR' }))).toBe(false)
  })
})

describe('반품·교환 권한 (SAL-03·06)', () => {
  it('판매한 매장의 매장 계정은 오늘 마감 전이면 지난 날 판매도 반품한다', () => {
    expect(canReturnSale(sale(1, '2026-11-01T10:00:00+09:00'), ctx())).toBe(true)
  })

  it('오늘이 마감되면 반품하지 못한다', () => {
    expect(canReturnSale(sale(1), ctx({ todayClosed: true }))).toBe(false)
  })

  it('다른 매장 판매는 반품하지 못한다', () => {
    expect(canReturnSale(sale(2), ctx())).toBe(false)
  })

  it('본사와 직원 개인 계정은 반품하지 못한다', () => {
    expect(canReturnSale(sale(1), ctx({ role: 'HQ', storeId: null }))).toBe(false)
    expect(canReturnSale(sale(1), ctx({ role: 'SENIOR' }))).toBe(false)
  })
})

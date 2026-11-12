import { describe, it, expect } from 'vitest'
import { defaultLineAmount, recalcLines, totals } from './saleAmount'

describe('결제 금액 기본값 (SAL-01, BR-10)', () => {
  it('정가 × 수량 × (1 − 할인율)이다', () => {
    expect(defaultLineAmount({ listPrice: 798000, qty: 1, discountRate: 3 })).toBe(774060)
    expect(defaultLineAmount({ listPrice: 100000, qty: 2, discountRate: 5 })).toBe(190000)
  })

  it('비회원·일반 등급(할인 0%)이면 정가 × 수량이다', () => {
    expect(defaultLineAmount({ listPrice: 59000, qty: 3, discountRate: 0 })).toBe(177000)
  })

  it('원 미만은 버린다', () => {
    // 33,333 × 0.97 = 32,333.01 → 32,333
    expect(defaultLineAmount({ listPrice: 33333, qty: 1, discountRate: 3 })).toBe(32333)
    // 19,999 × 0.93 = 18,599.07 → 18,599
    expect(defaultLineAmount({ listPrice: 19999, qty: 1, discountRate: 7 })).toBe(18599)
  })

  it('소수 계산 오차로 1원이 깎이지 않는다', () => {
    // 1,000 × 0.97 을 소수로 계산하면 969.9999… 가 될 수 있다
    expect(defaultLineAmount({ listPrice: 1000, qty: 1, discountRate: 3 })).toBe(970)
    expect(defaultLineAmount({ listPrice: 10, qty: 7, discountRate: 10 })).toBe(63)
  })

  it('직접 입력 상품(정가 없음)은 기본값이 없다', () => {
    expect(defaultLineAmount({ listPrice: null, qty: 1, discountRate: 3 })).toBeNull()
  })
})

describe('고객·수량이 바뀔 때 다시 계산 (S4)', () => {
  const lines = [
    { key: 'a', listPrice: 798000, qty: 1, lineAmount: 798000, edited: false },
    { key: 'b', listPrice: 100000, qty: 1, lineAmount: 90000, edited: true },
    { key: 'c', listPrice: null, qty: 1, lineAmount: 217800, edited: false },
  ]

  it('고치지 않은 등록 상품 줄만 다시 계산한다', () => {
    const next = recalcLines(lines, 3)
    expect(next[0].lineAmount).toBe(774060)
    expect(next[1].lineAmount).toBe(90000) // 직원이 고친 줄은 그대로
    expect(next[2].lineAmount).toBe(217800) // 직접 입력 줄은 그대로
  })

  it('원래 배열을 바꾸지 않는다', () => {
    recalcLines(lines, 3)
    expect(lines[0].lineAmount).toBe(798000)
  })
})

describe('합계', () => {
  it('수량과 결제 금액을 더한다. 빈 금액은 0으로 센다', () => {
    const t = totals([
      { qty: 1, lineAmount: 774060 },
      { qty: 2, lineAmount: null },
    ])
    expect(t).toEqual({ qty: 3, amount: 774060 })
  })
})

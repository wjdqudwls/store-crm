import { describe, it, expect } from 'vitest'
import { exchangeDiff, leftAmount, leftQty, refundAmount, refundTotal } from './returnAmount'

const line = (qty, lineAmount, returnedQty = 0, returnedAmount = 0) => ({
  lineId: 1,
  qty,
  lineAmount,
  returnedQty,
  returnedAmount,
})

describe('남은 수량·금액', () => {
  it('판매 수량에서 반품 수량을 뺀다', () => {
    expect(leftQty(line(3, 300, 1, 100))).toBe(2)
    expect(leftAmount(line(3, 300, 1, 100))).toBe(200)
  })
})

describe('반품 환불 금액 (BR-11)', () => {
  it('결제 금액 ÷ 판매 수량 × 반품 수량이다', () => {
    expect(refundAmount(line(2, 556140), 1)).toBe(278070)
  })

  it('원 미만은 버린다', () => {
    expect(refundAmount(line(3, 100), 1)).toBe(33)
    expect(refundAmount(line(3, 100), 2)).toBe(66)
  })

  it('마지막 남은 수량을 반품하면 남은 결제 금액을 전부 돌려준다', () => {
    // 3개 100원 → 33원, 33원, 34원
    expect(refundAmount(line(3, 100, 2, 66), 1)).toBe(34)
    // 한 번에 다 반품해도 결제 금액 그대로
    expect(refundAmount(line(3, 100), 3)).toBe(100)
  })

  it('반품 수량이 0이면 0원이다', () => {
    expect(refundAmount(line(1, 774060), 0)).toBe(0)
  })

  it('남은 수량보다 많으면 계산하지 않는다', () => {
    expect(() => refundAmount(line(2, 200, 1, 100), 2)).toThrow(
      '남은 수량보다 많이 반품할 수 없습니다',
    )
  })

  it('고른 줄의 환불 금액을 더한다', () => {
    const lines = [
      { ...line(1, 774060), lineId: 1 },
      { ...line(3, 100, 2, 66), lineId: 2 },
      { ...line(2, 500), lineId: 3 },
    ]
    expect(refundTotal(lines, { 1: 1, 2: 1 })).toBe(774094)
    expect(refundTotal(lines, {})).toBe(0)
  })
})

describe('교환 차액 (SAL-06)', () => {
  it('새 상품 합계가 크면 추가 결제', () => {
    expect(exchangeDiff(774060, 871060)).toEqual({ kind: 'EXTRA', amount: 97000 })
  })

  it('새 상품 합계가 작으면 차액 환불', () => {
    expect(exchangeDiff(774060, 724060)).toEqual({ kind: 'REFUND', amount: 50000 })
  })

  it('같으면 차액 없음', () => {
    expect(exchangeDiff(500000, 500000)).toEqual({ kind: 'EVEN', amount: 0 })
  })
})

/**
 * 환불·교환 금액 (SAL-03·06, BR-11). 화면의 예상 금액과 가짜 API가 같은 함수를 쓴다.
 * 최종 금액은 서버 응답이다.
 *
 * @typedef {{ lineId: number, qty: number, lineAmount: number, returnedQty: number, returnedAmount: number }} SaleLine
 */

/** @param {SaleLine} line */
export const leftQty = (line) => line.qty - line.returnedQty

/** @param {SaleLine} line */
export const leftAmount = (line) => line.lineAmount - line.returnedAmount

/**
 * 환불 금액 = 결제 금액 ÷ 판매 수량 × 반품 수량, 원 미만 버림.
 * 마지막 남은 수량이면 남은 결제 금액 전부 (나눠 반품해도 합계가 결제 금액과 같게).
 * @param {SaleLine} line
 * @param {number} qty 반품 수량
 */
export function refundAmount(line, qty) {
  if (!qty) return 0
  const left = leftQty(line)
  if (qty > left) throw new RangeError('남은 수량보다 많이 반품할 수 없습니다')
  if (qty === left) return leftAmount(line)
  return Math.floor((line.lineAmount * qty) / line.qty)
}

/**
 * @param {SaleLine[]} lines
 * @param {Record<number, number>} qtyByLineId 줄마다 반품 수량
 */
export function refundTotal(lines, qtyByLineId) {
  return lines.reduce((sum, l) => sum + refundAmount(l, qtyByLineId[l.lineId] ?? 0), 0)
}

/**
 * 교환 차액. 새 상품 합계 − 반품 금액이 양수면 추가 결제, 음수면 차액 환불 (03 4.8 `extraPaid`).
 * @param {number} returned 반품 금액
 * @param {number} newTotal 새 상품 합계
 * @returns {{ kind: 'EXTRA' | 'REFUND' | 'EVEN', amount: number }}
 */
export function exchangeDiff(returned, newTotal) {
  const d = newTotal - returned
  if (d > 0) return { kind: 'EXTRA', amount: d }
  if (d < 0) return { kind: 'REFUND', amount: -d }
  return { kind: 'EVEN', amount: 0 }
}

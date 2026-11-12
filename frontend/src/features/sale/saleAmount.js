/**
 * 결제 금액 기본값 (SAL-01, BR-10). 서버는 계산하지 않고 화면이 채운 값을 그대로 기록한다 (ADR-014).
 * 할인율은 정수 %라서 정수로만 계산해 소수 오차를 피한다.
 *
 * @param {{ listPrice: number|null, qty: number, discountRate: number }} p
 * @returns {number|null} 원 미만 버림. 정가가 없는 직접 입력 상품이면 null
 */
export function defaultLineAmount({ listPrice, qty, discountRate }) {
  if (listPrice == null) return null
  return Math.floor((listPrice * qty * (100 - discountRate)) / 100)
}

/**
 * 고객·수량이 바뀌면 고치지 않은(edited=false) 등록 상품 줄만 다시 계산한다 (S4).
 * @template {{ listPrice: number|null, qty: number, lineAmount: number|null, edited: boolean }} L
 * @param {L[]} lines
 * @param {number} discountRate
 * @returns {L[]}
 */
export function recalcLines(lines, discountRate) {
  return lines.map((line) =>
    line.edited || line.listPrice == null
      ? line
      : { ...line, lineAmount: defaultLineAmount({ ...line, discountRate }) },
  )
}

/** @param {{ qty: number, lineAmount: number|null }[]} lines */
export function totals(lines) {
  return lines.reduce((t, l) => ({ qty: t.qty + l.qty, amount: t.amount + (l.lineAmount ?? 0) }), {
    qty: 0,
    amount: 0,
  })
}

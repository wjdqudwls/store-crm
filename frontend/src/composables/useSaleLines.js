import { computed, ref, unref, watch } from 'vue'
import { defaultLineAmount, recalcLines, totals } from '@/features/sale/saleAmount'

/**
 * 담은 상품 줄 (05 SCR-02). 구매 모드·교환 모드의 새 상품·정정 모드가 같이 쓴다.
 * 결제 금액 기본값은 정가 × 수량 × (1 − 할인율), 직원이 고친 줄(edited)은 다시 계산하지 않는다 (S4).
 *
 * @param {import('vue').MaybeRef<number>} discountRate 판매 전 등급의 할인율 (BR-10)
 */
export function useSaleLines(discountRate) {
  const lines = ref([])
  let seq = 0

  /** 상품 고르기에서 담은 상품. 처음 결제 금액은 기본값 */
  function addLine(item) {
    lines.value.push({
      key: ++seq,
      ...item,
      qty: 1,
      lineAmount: defaultLineAmount({
        listPrice: item.listPrice,
        qty: 1,
        discountRate: unref(discountRate),
      }),
      edited: false,
      memberId: null,
    })
  }

  /** 정정처럼 이미 있는 줄을 그대로 넣는다. 결제 금액은 원래 값이고 다시 계산하지 않는다 */
  function setLines(items) {
    lines.value = items.map((item) => ({ key: ++seq, memberId: null, ...item, edited: true }))
  }

  function removeLine(key) {
    lines.value = lines.value.filter((l) => l.key !== key)
  }

  function changeQty(line, qty) {
    line.qty = qty
    if (!line.edited && line.listPrice != null) {
      line.lineAmount = defaultLineAmount({ ...line, discountRate: unref(discountRate) })
    }
  }

  /** 직원이 금액을 고치면 "고침" 표시(touched), 그 뒤에는 다시 계산하지 않는다 (S4) */
  function changeAmount(line, value) {
    line.lineAmount = value
    line.edited = true
    line.touched = true
  }

  function changeWearer(line, memberId) {
    line.memberId = memberId ?? null
  }

  function clearWearers() {
    lines.value.forEach((l) => (l.memberId = null))
  }

  function reset() {
    lines.value = []
  }

  // 고객이 바뀌면 고치지 않은 줄만 다시 계산. 할인은 판매 전 등급 기준 (BR-10)
  watch(
    () => unref(discountRate),
    (rate) => {
      lines.value = recalcLines(lines.value, rate)
    },
  )

  const sum = computed(() => totals(lines.value))
  const invalid = computed(() => lines.value.some((l) => l.lineAmount == null || l.lineAmount < 0))

  /** POST /sales·교환·정정 본문의 줄 (03 4.1). 등록 상품은 skuId, 직접 입력은 상품 칸 */
  const toBody = () =>
    lines.value.map((l) =>
      l.skuId
        ? {
            skuId: l.skuId,
            qty: l.qty,
            lineAmount: l.lineAmount,
            memberId: l.memberId ?? undefined,
          }
        : {
            productName: l.productName,
            itemType: l.itemType,
            size: l.size,
            color: l.color ?? undefined,
            target: l.target ?? undefined,
            qty: l.qty,
            lineAmount: l.lineAmount,
            memberId: l.memberId ?? undefined,
          },
    )

  return {
    lines,
    sum,
    invalid,
    addLine,
    setLines,
    removeLine,
    changeQty,
    changeAmount,
    changeWearer,
    clearWearers,
    reset,
    toBody,
  }
}

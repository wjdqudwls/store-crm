import { computed, ref } from 'vue'
import { getDailySummary } from '@/api/sales'
import { canChangeSale, canReturnSale, isOwnStoreSale } from '@/features/sale/saleChange'
import { useSessionStore } from '@/stores/session'

/**
 * 판매를 고칠 수 있는지 화면에서 계산한다 (BR-16). 서버는 03에 "수정 가능" 칸이 없어서
 * `GET /sales/daily-summary`의 오늘 마감 여부와 판매 날짜로 판단하고, 서버도 같은 규칙을 다시 검사한다.
 */
export function useSaleChangeRules() {
  const session = useSessionStore()
  const todayClosed = ref(false)
  if (session.isStore) getDailySummary().then((d) => (todayClosed.value = d.closed))

  const ctx = () => ({
    role: session.me.role,
    storeId: session.me.storeId,
    todayClosed: todayClosed.value,
    today: new Date().toLocaleDateString('sv-SE'),
  })
  return {
    todayClosed,
    canChange: (sale) => canChangeSale(sale, ctx()),
    canReturn: (sale) => canReturnSale(sale, ctx()),
    isOwnStore: (sale) => isOwnStoreSale(sale, session.me.storeId),
    computedCanChange: (saleRef) =>
      computed(() => saleRef.value && canChangeSale(saleRef.value, ctx())),
  }
}

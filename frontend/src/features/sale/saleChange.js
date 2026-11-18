/**
 * 판매를 고칠 수 있는지 (BR-16, 05 SCR-03 W8). 서버도 같은 규칙을 다시 검사한다 (B2-3·B2-6).
 * 화면은 `GET /sales/daily-summary`의 마감 여부와 판매 날짜로 계산한다.
 */

/** 매장 계정은 자기 매장의 판매만 환불·교환·정정·기록 취소한다. 다른 매장 영수증은 보기만 */
export function isOwnStoreSale(sale, myStoreId) {
  return sale.store.id === myStoreId
}

/**
 * 반품·교환: 판매한 매장의 매장 계정만, 오늘이 마감 전일 때 (판매한 날은 묻지 않는다: 기록하는 날이 마감되면 못 한다, SAL-03·06).
 * 본사는 반품·교환을 하지 않는다.
 * @param {{ store: { id: number } }} sale
 * @param {{ role: string, storeId: number|null, todayClosed: boolean }} ctx
 */
export function canReturnSale(sale, { role, storeId, todayClosed }) {
  return role === 'STORE' && isOwnStoreSale(sale, storeId) && !todayClosed
}

/**
 * 정정·기록 취소: 판매한 매장의 매장 계정은 판매한 날·마감 전, 본사는 언제나 (BR-16).
 * @param {{ store: { id: number }, soldAt: string }} sale
 * @param {{ role: string, storeId: number|null, todayClosed: boolean, today: string }} ctx
 *   today = 오늘 날짜 `YYYY-MM-DD`, todayClosed = 내 매장의 오늘 마감 여부
 */
export function canChangeSale(sale, { role, storeId, todayClosed, today }) {
  if (role === 'HQ') return true
  if (role !== 'STORE') return false
  return isOwnStoreSale(sale, storeId) && sale.soldAt.slice(0, 10) === today && !todayClosed
}

import { fakeResponse, fakeError } from './fake'
import { staff, customers, products, gradeDiscounts } from '@/mocks/data'
import { sales, stores, dayCloses, todayKey, toIso, toYmd } from '@/mocks/sales'
import { useSessionStore } from '@/stores/session'

/**
 * 판매 API (05 SCR-02·03). 지금은 가짜 데이터이고 12단계에서 axios로 바꾼다 (ADR-032).
 * 판매 기록·판매 내역·고객 구매이력이 mocks/sales.js의 목록 하나를 같이 쓴다.
 */

const savedKeys = new Map()
const me = () => useSessionStore().me
const page = (content) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
})
const dateOf = (isoText) => isoText.slice(0, 10)
const denied = () => fakeError(403, 'ACCESS_DENIED', '이 계정으로는 할 수 없는 일입니다.')

/** GET /stores/{storeId}/staff (매장 계정이 부르면 활성 직원만) */
export function getStaff() {
  return fakeResponse(staff)
}

/** 오늘 마감 상태: 마감 행이 있고 다시 열지 않았으면 마감 */
function closeRow(storeId, date = todayKey()) {
  return dayCloses.find((d) => d.storeId === storeId && d.date === date && !d.reopenedAt)
}

function todayCount(storeId) {
  const today = todayKey()
  return sales.filter(
    (s) => s.store.id === storeId && dateOf(s.soldAt) === today && s.status !== 'VOIDED',
  ).length
}

function summaryOf(storeId) {
  const row = closeRow(storeId)
  return {
    storeId,
    date: todayKey(),
    saleCount: todayCount(storeId),
    closed: Boolean(row),
    closedAt: row?.closedAt ?? null,
  }
}

/** GET /sales/daily-summary?date=오늘 (03 4.12) */
export function getDailySummary(storeId) {
  return fakeResponse(summaryOf(storeId ?? me().storeId ?? 1))
}

/** 본사 매장 목록용: 모든 매장의 오늘 요약 */
export function getDailySummaries() {
  return fakeResponse(stores.map((s) => summaryOf(s.id)))
}

/** GET /customers/{id} 중 판매 기록에 쓰는 부분(등급·할인율) + GET /customers/{id}/members */
export function getCustomerForSale(id) {
  const c = customers.find((x) => x.id === id)
  if (!c) return fakeError(404, 'CUSTOMER_NOT_FOUND', '고객을 찾을 수 없습니다')
  return fakeResponse({
    id: c.id,
    name: c.name,
    phoneMasked: c.phoneMasked,
    grade: { current: c.grade, discountRate: gradeDiscounts[c.grade] },
    members: c.members,
  })
}

/** GET /products?keyword= : 상품명·품번 */
export function searchProducts(keyword) {
  const q = keyword.trim().toLowerCase()
  const content = products.filter(
    (p) => q && (p.name.toLowerCase().includes(q) || p.productCode?.toLowerCase().includes(q)),
  )
  return fakeResponse(page(content))
}

/**
 * POST /sales (03 4.1). 같은 요청 키로 다시 오면 처음 응답을 그대로 준다.
 * @param {object} body
 * @param {string} idempotencyKey
 */
export function createSale(body, idempotencyKey) {
  if (savedKeys.has(idempotencyKey)) return fakeResponse(savedKeys.get(idempotencyKey), 300)
  const storeId = me().storeId ?? 1
  if (closeRow(storeId)) {
    return fakeError(409, 'DAY_CLOSED', '오늘 판매는 마감되었습니다. 다시 열려면 본사에 요청하세요')
  }

  const store = stores.find((s) => s.id === storeId)
  const customer = customers.find((c) => c.id === body.customerId)
  const now = body.soldAt ? new Date(body.soldAt) : new Date()
  const seq =
    sales.filter((s) => s.store.id === storeId && dateOf(s.soldAt) === todayKey()).length + 1
  const lines = body.lines.map((l, i) => {
    const product = products.find((p) => p.skus.some((s) => s.skuId === l.skuId))
    const sku = product?.skus.find((s) => s.skuId === l.skuId)
    return {
      lineId: i + 1,
      skuId: l.skuId ?? null,
      productCode: product?.productCode ?? null,
      productName: product?.name ?? l.productName,
      itemType: product?.itemType ?? l.itemType,
      color: sku?.color ?? l.color ?? null,
      size: sku?.size ?? l.size,
      listPrice: product?.listPrice ?? null,
      qty: l.qty,
      lineAmount: l.lineAmount,
      returnedQty: 0,
      returnedAmount: 0,
      wearerLabel: customer?.members.find((m) => m.id === l.memberId)?.label ?? null,
    }
  })
  const sale = {
    id: 90000 + sales.length,
    receiptNo: `${store.code}-${toYmd(now)}-${String(seq).padStart(4, '0')}`,
    soldAt: toIso(now),
    store: { id: store.id, name: store.name },
    kind: 'NORMAL',
    status: 'COMPLETED',
    customerId: customer?.id ?? null,
    customerName: customer?.name ?? null,
    sellerStaffId: body.sellerStaffId,
    sellerName: staff.find((s) => s.id === body.sellerStaffId)?.name ?? null,
    appliedGrade: customer?.grade ?? null,
    lines,
    returns: [],
    originSaleId: null,
    originReceiptNo: null,
    totalAmount: lines.reduce((s, l) => s + l.lineAmount, 0),
    totalQty: lines.reduce((s, l) => s + l.qty, 0),
  }
  sales.unshift(sale)
  if (customer) customer.lastVisitAt = sale.soldAt
  savedKeys.set(idempotencyKey, sale)
  return fakeResponse(sale, 300)
}

/** GET /sales?from=&to= (최신순). 매장 계정은 자기 매장, 본사는 storeId */
export function getSales({ from, to, storeId } = {}) {
  const sid = me().role === 'HQ' ? storeId : (me().storeId ?? 1)
  const list = sales
    .filter((s) => (!sid || s.store.id === sid) && (!from || dateOf(s.soldAt) >= from))
    .filter((s) => !to || dateOf(s.soldAt) <= to)
    .map((s) => ({
      id: s.id,
      receiptNo: s.receiptNo,
      soldAt: s.soldAt,
      kind: s.kind,
      status: s.status,
      customerId: s.customerId,
      customerName: s.customerName,
      sellerStaffId: s.sellerStaffId,
      sellerName: s.sellerName,
      totalQty: s.totalQty,
      totalAmount: s.totalAmount,
      originSaleId: s.originSaleId,
      originReceiptNo: s.originReceiptNo,
    }))
  return fakeResponse(page(list))
}

/** GET /sales/{id} */
export function getSale(id) {
  const s = sales.find((x) => x.id === id)
  if (!s) return fakeError(404, 'SALE_NOT_FOUND', '판매를 찾을 수 없습니다.')
  return fakeResponse(s)
}

/** 판매를 고칠 수 있는지 (W8): 매장 계정은 판매한 날·마감 전, 본사는 언제나 */
export function canChangeSale(sale, role) {
  if (role === 'HQ') return true
  if (role !== 'STORE') return false
  return dateOf(sale.soldAt) === todayKey() && !closeRow(sale.store.id)
}

/**
 * POST /sales/{id}/returns (03 4.2). 줄마다 반품 수량. 환불 금액은 결제 금액을 수량으로 나눠 내림.
 * @param {number} id
 * @param {{ staffId: number, lines: { lineId: number, qty: number }[] }} body
 */
export function returnSale(id, body) {
  const s = sales.find((x) => x.id === id)
  if (!s) return fakeError(404, 'SALE_NOT_FOUND', '판매를 찾을 수 없습니다.')
  if (me().role !== 'STORE' || !canChangeSale(s, me().role)) return denied()
  for (const r of body.lines) {
    const l = s.lines.find((x) => x.lineId === r.lineId)
    if (!l || r.qty > l.qty - l.returnedQty) {
      return fakeError(409, 'RETURN_QTY_EXCEEDED', '남은 수량보다 많이 반품할 수 없습니다.')
    }
  }
  let amount = 0
  for (const r of body.lines) {
    const l = s.lines.find((x) => x.lineId === r.lineId)
    const refund = Math.floor((l.lineAmount * r.qty) / l.qty)
    l.returnedQty += r.qty
    l.returnedAmount += refund
    amount += refund
  }
  s.status = s.lines.every((l) => l.returnedQty === l.qty) ? 'RETURNED' : 'PARTIAL_RETURNED'
  const ret = {
    returnId: Date.now(),
    kind: 'RETURN',
    returnedAt: toIso(new Date()),
    staffName: staff.find((x) => x.id === body.staffId)?.name ?? null,
    amount,
    newSaleId: null,
    newReceiptNo: null,
  }
  s.returns.push(ret)
  return fakeResponse({ returnId: ret.returnId, returnedAmount: amount, status: s.status }, 300)
}

/** POST /sales/{id}/void (03 4.9) */
export function voidSale(id, body) {
  const s = sales.find((x) => x.id === id)
  if (!s) return fakeError(404, 'SALE_NOT_FOUND', '판매를 찾을 수 없습니다.')
  if (!canChangeSale(s, me().role)) return denied()
  if (s.status === 'VOIDED') {
    return fakeError(409, 'SALE_ALREADY_VOIDED', '이미 기록 취소된 판매입니다.')
  }
  s.status = 'VOIDED'
  // 마지막 방문일에서 빠진다 (BR-09): 남은 판매 중 가장 최근으로 되돌린다
  const customer = customers.find((c) => c.id === s.customerId)
  if (customer && customer.lastVisitAt === s.soldAt) {
    customer.lastVisitAt =
      sales.find((x) => x.customerId === customer.id && x.status !== 'VOIDED')?.soldAt ?? null
  }
  s.returns.push({
    returnId: Date.now(),
    kind: 'VOID',
    returnedAt: toIso(new Date()),
    staffName: staff.find((x) => x.id === body.staffId)?.name ?? me().name,
    amount: s.totalAmount,
    newSaleId: null,
    newReceiptNo: null,
  })
  return fakeResponse(s, 300)
}

/** POST /day-closes (03 4.10): 오늘 판매 마감 */
export function closeDay() {
  const storeId = me().storeId ?? 1
  if (closeRow(storeId)) return fakeError(409, 'DAY_ALREADY_CLOSED', '이미 마감했습니다.')
  const row = {
    storeId,
    date: todayKey(),
    closedAt: toIso(new Date()),
    closedByName: me().name,
    reopenedAt: null,
    reopenedByName: null,
  }
  dayCloses.unshift(row)
  return fakeResponse(row, 300)
}

/** GET /stores/{storeId}/day-closes (최신순) */
export function getDayCloses(storeId) {
  return fakeResponse(
    dayCloses.filter((d) => d.storeId === storeId).sort((a, b) => b.date.localeCompare(a.date)),
  )
}

/** DELETE /stores/{storeId}/day-closes/{date}: 본사가 다시 연다 */
export function reopenDay(storeId, date) {
  if (me().role !== 'HQ') return denied()
  const row = closeRow(storeId, date)
  if (!row) return fakeError(404, 'DAY_CLOSE_NOT_FOUND', '마감 기록이 없습니다.')
  row.reopenedAt = toIso(new Date())
  row.reopenedByName = me().name
  return fakeResponse(row, 300)
}

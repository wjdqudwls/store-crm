import { fakeResponse, fakeError } from './fake'
import { staff, customers, products, gradeDiscounts } from '@/mocks/data'
import { sales, stores, dayCloses, todayKey, toIso, toYmd } from '@/mocks/sales'
import { useSessionStore } from '@/stores/session'
import { leftQty, refundAmount } from '@/features/sale/returnAmount'
import { canChangeSale as canChange, isOwnStoreSale as ownStore } from '@/features/sale/saleChange'

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
  return fakeResponse([summaryOf(storeId ?? me().storeId ?? 1)]) // 03: 배열
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

/** 판매 라인 본문(03 4.1) → 영수증 라인 */
function toLines(bodyLines, customer) {
  return bodyLines.map((l, i) => {
    const product = products.find((p) => p.skus.some((s) => s.skuId === l.skuId))
    const sku = product?.skus.find((s) => s.skuId === l.skuId)
    return {
      lineId: i + 1,
      skuId: l.skuId ?? null,
      productCode: product?.productCode ?? l.productCode ?? null,
      productName: product?.name ?? l.productName,
      itemType: product?.itemType ?? l.itemType,
      color: sku?.color ?? l.color ?? null,
      size: sku?.size ?? l.size,
      listPrice: product?.listPrice ?? l.listPrice ?? null,
      qty: l.qty,
      lineAmount: l.lineAmount,
      returnedQty: 0,
      returnedAmount: 0,
      wearerLabel: customer?.members.find((m) => m.id === l.memberId)?.label ?? null,
    }
  })
}

/** 새 영수증을 만들어 목록 앞에 넣는다. 영수증 번호는 판매일의 다음 순번 */
function addSale({ storeId, soldAt, customerId, sellerStaffId, lines, kind = 'NORMAL', origin }) {
  const store = stores.find((s) => s.id === storeId)
  const customer = customers.find((c) => c.id === customerId)
  const day = toYmd(soldAt)
  const seq =
    sales.filter((s) => s.store.id === storeId && s.receiptNo.includes(`-${day}-`)).length + 1
  const saleLines = toLines(lines, customer)
  const sale = {
    id: 90000 + sales.length,
    receiptNo: `${store.code}-${day}-${String(seq).padStart(4, '0')}`,
    soldAt: toIso(soldAt),
    store: { id: store.id, name: store.name },
    kind,
    status: 'COMPLETED',
    customerId: customer?.id ?? null,
    customerName: customer?.name ?? null,
    sellerStaffId,
    sellerName: staff.find((s) => s.id === sellerStaffId)?.name ?? null,
    appliedGrade: customer?.grade ?? null,
    lines: saleLines,
    returns: [],
    originSaleId: origin?.id ?? null,
    originReceiptNo: origin?.receiptNo ?? null,
    totalAmount: saleLines.reduce((s, l) => s + l.lineAmount, 0),
    totalQty: saleLines.reduce((s, l) => s + l.qty, 0),
  }
  sales.unshift(sale)
  sales.sort((a, b) => b.soldAt.localeCompare(a.soldAt))
  if (customer && (!customer.lastVisitAt || customer.lastVisitAt < sale.soldAt)) {
    customer.lastVisitAt = sale.soldAt
  }
  return sale
}

const dayClosedError = () =>
  fakeError(409, 'DAY_CLOSED', '오늘 판매는 마감되었습니다. 다시 열려면 본사에 요청하세요')

/**
 * POST /sales (03 4.1). 같은 요청 키로 다시 오면 처음 응답을 그대로 준다.
 * @param {object} body
 * @param {string} idempotencyKey
 */
export function createSale(body, idempotencyKey) {
  if (savedKeys.has(idempotencyKey)) return fakeResponse(savedKeys.get(idempotencyKey), 300)
  const storeId = me().storeId ?? 1
  if (closeRow(storeId)) return dayClosedError()

  const sale = addSale({
    storeId,
    soldAt: body.soldAt ? new Date(body.soldAt) : new Date(),
    customerId: body.customerId,
    sellerStaffId: body.sellerStaffId,
    lines: body.lines,
  })
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

/** 지금 로그인한 계정 기준으로 판매를 고칠 수 있는지 (규칙은 features/sale/saleChange.js) */
const changeCtx = () => {
  const storeId = me().storeId ?? null
  return {
    role: me().role,
    storeId,
    todayClosed: Boolean(storeId && closeRow(storeId)),
    today: todayKey(),
  }
}
const canChangeSale = (sale) => canChange(sale, changeCtx())
const isOwnStoreSale = (sale) => ownStore(sale, me().storeId ?? 1)

/** 반품·교환 전에 확인할 것: 판매 있음, 매장 계정이 고칠 수 있는 날, 기록 취소 아님, 남은 수량 */
function checkReturn(s, returnLines) {
  if (!s) return fakeError(404, 'SALE_NOT_FOUND', '판매를 찾을 수 없습니다.')
  if (me().role !== 'STORE' || !isOwnStoreSale(s)) return denied()
  if (closeRow(s.store.id)) return dayClosedError()
  if (s.status === 'VOIDED') {
    return fakeError(409, 'SALE_ALREADY_VOIDED', '이미 기록 취소된 판매입니다.')
  }
  if (!returnLines?.length) {
    return fakeError(400, 'VALIDATION_FAILED', '반품할 줄을 1개 이상 고르세요.')
  }
  for (const r of returnLines) {
    const l = s.lines.find((x) => x.lineId === r.lineId)
    if (!l) return fakeError(400, 'VALIDATION_FAILED', '이 영수증의 줄이 아닙니다.')
    if (r.qty < 1 || r.qty > leftQty(l)) {
      return fakeError(409, 'RETURN_QTY_EXCEEDED', '남은 수량보다 많이 반품할 수 없습니다.')
    }
  }
  return null
}

/** 줄마다 반품 수량을 반영하고 반품 행을 남긴다 (BR-11) */
function applyReturn(s, returnLines, { kind, staffId, newSale = null }) {
  let amount = 0
  for (const r of returnLines) {
    const l = s.lines.find((x) => x.lineId === r.lineId)
    const refund = refundAmount(l, r.qty)
    l.returnedQty += r.qty
    l.returnedAmount += refund
    amount += refund
  }
  s.status = s.lines.every((l) => l.returnedQty === l.qty) ? 'RETURNED' : 'PARTIAL_RETURNED'
  const ret = {
    returnId: Date.now(),
    kind,
    returnedAt: toIso(new Date()),
    staffName: staff.find((x) => x.id === staffId)?.name ?? null,
    amount,
    newSaleId: newSale?.id ?? null,
    newReceiptNo: newSale?.receiptNo ?? null,
  }
  s.returns.push(ret)
  return ret
}

/**
 * POST /sales/{id}/returns (03 4.2). 줄마다 반품 수량. 환불 금액은 BR-11.
 * @param {number} id
 * @param {{ staffId: number, lines: { lineId: number, qty: number }[] }} body
 * @param {string} [idempotencyKey]
 */
export function returnSale(id, body, idempotencyKey) {
  if (idempotencyKey && savedKeys.has(idempotencyKey)) {
    return fakeResponse(savedKeys.get(idempotencyKey), 300)
  }
  const s = sales.find((x) => x.id === id)
  const err = checkReturn(s, body.lines)
  if (err) return err
  const ret = applyReturn(s, body.lines, { kind: 'RETURN', staffId: body.staffId })
  const res = { returnId: ret.returnId, returnedAmount: ret.amount, status: s.status }
  if (idempotencyKey) savedKeys.set(idempotencyKey, res)
  return fakeResponse(res, 300)
}

/**
 * POST /sales/{id}/exchanges (03 4.8). 돌려받을 줄을 반품하고 새 상품을 새 영수증(kind=EXCHANGE)으로.
 * 처리 직원이 새 상품의 판매 사원이다.
 * @param {number} id
 * @param {{ staffId: number, returnLines: { lineId: number, qty: number }[], newLines: object[] }} body
 * @param {string} idempotencyKey
 */
export function exchangeSale(id, body, idempotencyKey) {
  if (savedKeys.has(idempotencyKey)) return fakeResponse(savedKeys.get(idempotencyKey), 300)
  const s = sales.find((x) => x.id === id)
  const err = checkReturn(s, body.returnLines)
  if (err) return err
  if (!body.newLines?.length) {
    return fakeError(400, 'VALIDATION_FAILED', '새 상품을 1개 이상 담으세요.')
  }
  const newSale = addSale({
    storeId: s.store.id,
    soldAt: new Date(),
    customerId: s.customerId,
    sellerStaffId: body.staffId,
    lines: body.newLines,
    kind: 'EXCHANGE',
    origin: s,
  })
  const ret = applyReturn(s, body.returnLines, {
    kind: 'EXCHANGE',
    staffId: body.staffId,
    newSale,
  })
  const res = {
    returnId: ret.returnId,
    returnedAmount: ret.amount,
    newSale,
    extraPaid: newSale.totalAmount - ret.amount,
  }
  savedKeys.set(idempotencyKey, res)
  return fakeResponse(res, 300)
}

/**
 * PUT /sales/{id} (03 4.9) 정정. 원 판매를 기록 취소하고 원래 시각·판매 사원으로 새 영수증(kind=CORRECTION).
 * 판매 사원과 응대 메모는 받지 않는다 (BR-16).
 * @param {number} id
 * @param {{ staffId?: number, customerId?: number, lines: object[] }} body
 * @param {string} idempotencyKey
 */
export function correctSale(id, body, idempotencyKey) {
  if (savedKeys.has(idempotencyKey)) return fakeResponse(savedKeys.get(idempotencyKey), 300)
  const s = sales.find((x) => x.id === id)
  if (!s) return fakeError(404, 'SALE_NOT_FOUND', '판매를 찾을 수 없습니다.')
  if (me().role === 'STORE' && !isOwnStoreSale(s)) return denied()
  if (!canChangeSale(s)) {
    return fakeError(409, 'SALE_LOCKED', '마감된 판매는 본사가 고칩니다')
  }
  if (s.status === 'VOIDED') {
    return fakeError(409, 'SALE_ALREADY_VOIDED', '이미 기록 취소된 판매입니다.')
  }
  if (!s.lines.some((l) => leftQty(l) > 0)) {
    return fakeError(409, 'RETURN_QTY_EXCEEDED', '남은 줄이 없어 정정할 수 없습니다.')
  }
  if (!body.lines?.length) {
    return fakeError(400, 'VALIDATION_FAILED', '상품을 1개 이상 담으세요.')
  }
  const newSale = addSale({
    storeId: s.store.id,
    soldAt: new Date(s.soldAt),
    customerId: body.customerId,
    sellerStaffId: s.sellerStaffId,
    lines: body.lines,
    kind: 'CORRECTION',
    origin: s,
  })
  const amount = s.lines.reduce((sum, l) => sum + l.lineAmount - l.returnedAmount, 0)
  s.status = 'VOIDED'
  s.returns.push({
    returnId: Date.now(),
    kind: 'VOID',
    returnedAt: toIso(new Date()),
    staffName: staff.find((x) => x.id === body.staffId)?.name ?? me().name,
    amount,
    newSaleId: newSale.id,
    newReceiptNo: newSale.receiptNo,
  })
  savedKeys.set(idempotencyKey, newSale)
  return fakeResponse(newSale, 300)
}

/** POST /sales/{id}/void (03 4.9) */
export function voidSale(id, body) {
  const s = sales.find((x) => x.id === id)
  if (!s) return fakeError(404, 'SALE_NOT_FOUND', '판매를 찾을 수 없습니다.')
  if (!canChangeSale(s)) return denied()
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
    dayCloses
      .filter((d) => d.storeId === storeId)
      .sort((a, b) => b.date.localeCompare(a.date))
      .map(({ storeId: _storeId, ...row }) => row), // 03 마감 현황 항목에는 storeId가 없다
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

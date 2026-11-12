import { fakeResponse, fakeError } from './fake'
import { staff, customers, products, dailySummary, gradeDiscounts } from '@/mocks/data'

/**
 * 판매 기록 화면이 쓰는 API (05 SCR-02 "쓰는 API"). 지금은 가짜 데이터이고 12단계에서 axios로 바꾼다.
 */

let receiptSeq = dailySummary.saleCount
const savedKeys = new Map()

export function getStaff() {
  return fakeResponse(staff)
}

export function getDailySummary() {
  return fakeResponse({ ...dailySummary, saleCount: receiptSeq })
}

/** GET /customers?name= : 이름이 같은 고객 목록 */
export function searchCustomers(name) {
  const q = name.trim()
  const content = customers
    .filter((c) => q && c.name.includes(q))
    // 목록에는 전체 번호·가족을 넣지 않는다 (NFR-05)
    .map(({ id, name, phoneMasked, grade, lastVisitAt, staffName }) => ({
      id,
      name,
      phoneMasked,
      grade,
      lastVisitAt,
      staffName,
    }))
  return fakeResponse({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
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
    (p) => q && (p.name.toLowerCase().includes(q) || p.productCode.toLowerCase().includes(q)),
  )
  return fakeResponse({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
}

/**
 * POST /sales (03 4.1). 같은 요청 키로 다시 오면 처음 응답을 그대로 준다.
 * @param {object} body
 * @param {string} idempotencyKey
 */
export function createSale(body, idempotencyKey) {
  if (savedKeys.has(idempotencyKey)) return fakeResponse(savedKeys.get(idempotencyKey), 300)
  if (dailySummary.closed) {
    return fakeError(409, 'DAY_CLOSED', '오늘 판매는 마감되었습니다. 다시 열려면 본사에 요청하세요')
  }

  receiptSeq += 1
  const now = body.soldAt ? new Date(body.soldAt) : new Date()
  const ymd = now.toISOString().slice(0, 10).replaceAll('-', '')
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
    }
  })
  const res = {
    id: 88000 + receiptSeq,
    receiptNo: `GN01-${ymd}-${String(receiptSeq).padStart(4, '0')}`,
    soldAt: now.toISOString(),
    kind: 'NORMAL',
    sellerStaffId: body.sellerStaffId,
    customerId: body.customerId ?? null,
    lines,
    totalAmount: lines.reduce((s, l) => s + l.lineAmount, 0),
    totalQty: lines.reduce((s, l) => s + l.qty, 0),
  }
  savedKeys.set(idempotencyKey, res)
  return fakeResponse(res, 300)
}

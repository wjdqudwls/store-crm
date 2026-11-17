import { fakeResponse, fakeError } from './fake'
import { products } from '@/mocks/data'

/** 상품 API (05 SCR-05, PRD-01·02). 가짜 데이터이고 12단계에서 axios로 바꾼다 */

const page = (content) => ({
  content,
  page: 0,
  size: 50,
  totalElements: content.length,
  totalPages: 1,
})

/** GET /products?keyword= (빈 검색어면 전체) */
export function getProducts(keyword = '') {
  const q = keyword.trim().toLowerCase()
  const content = products.filter(
    (p) =>
      !q || p.name.toLowerCase().includes(q) || (p.productCode ?? '').toLowerCase().includes(q),
  )
  return fakeResponse(page(content))
}

/** GET /products/{id} */
export function getProduct(id) {
  const p = products.find((x) => x.id === id)
  return p ? fakeResponse(p) : fakeError(404, 'PRODUCT_NOT_FOUND', '상품을 찾을 수 없습니다.')
}

let nextSku = 7000
/** 색상 × 사이즈로 판매 단위(SKU)를 만든다. 이미 있는 조합은 ID를 그대로 둔다 */
function buildSkus(colors, sizes, old = []) {
  const cs = colors.length ? colors : ['']
  return cs.flatMap((color) =>
    sizes.map((size) => {
      const same = old.find((s) => (s.color ?? '') === color && s.size === size)
      return same ?? { skuId: ++nextSku, color: color || null, size }
    }),
  )
}

function dupCode(code, id) {
  return code && products.some((p) => p.productCode === code && p.id !== id)
}

/** POST /products. 같은 품번이면 409 PRODUCT_CODE_DUPLICATED */
export function createProduct(body) {
  if (dupCode(body.productCode, null)) {
    return fakeError(409, 'PRODUCT_CODE_DUPLICATED', '이미 등록된 품번입니다.')
  }
  const p = {
    id: Math.max(...products.map((x) => x.id)) + 1,
    productCode: body.productCode || null,
    name: body.name,
    itemType: body.itemType,
    target: body.target || null,
    listPrice: body.listPrice,
    skus: buildSkus(body.colors, body.sizes),
  }
  products.push(p)
  return fakeResponse(p, 300)
}

/** PATCH /products/{id}. 사이즈·색상을 빼도 과거 판매는 스냅샷이라 그대로다 */
export function updateProduct(id, body) {
  const p = products.find((x) => x.id === id)
  if (!p) return fakeError(404, 'PRODUCT_NOT_FOUND', '상품을 찾을 수 없습니다.')
  if (dupCode(body.productCode, id)) {
    return fakeError(409, 'PRODUCT_CODE_DUPLICATED', '이미 등록된 품번입니다.')
  }
  Object.assign(p, {
    productCode: body.productCode || null,
    name: body.name,
    itemType: body.itemType,
    target: body.target || null,
    listPrice: body.listPrice,
    skus: buildSkus(body.colors, body.sizes, p.skus),
  })
  return fakeResponse(p, 300)
}

/**
 * POST /products/import (03 4.6). 화면 초안에서는 파일을 읽지 않고 실패 예시를 돌려준다.
 * 실제 처리는 B1-2(Apache POI)에서 만든다.
 */
export function importProducts() {
  return fakeError(
    400,
    'PRODUCT_IMPORT_FAILED',
    '엑셀에 잘못된 행이 있어 아무것도 저장하지 않았습니다.',
    {
      details: {
        errors: [
          { row: 23, column: '사이즈', reason: "'95'는 여성 하의 사이즈가 아닙니다." },
          { row: 41, column: '품번', reason: '이미 등록된 품번입니다.' },
        ],
      },
      delayMs: 600,
    },
  )
}

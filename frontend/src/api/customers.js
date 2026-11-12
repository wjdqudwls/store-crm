import { fakeResponse, fakeError } from './fake'
import { customers, staff } from '@/mocks/data'

/**
 * 고객 API (지금은 가짜 데이터). 12단계에서 axios로 바꾼다 (ADR-032).
 */

let nextId = 2400

/** 비교용: 숫자와 + 만 남긴다. 내국인 010-… 은 그대로 비교 (서버는 E.164 해시로 비교, NFR-04) */
const phoneKey = (p) => String(p).replace(/[^\d+]/g, '')

/**
 * POST /customers (03 4.5 본문 + staffId). 같은 번호가 있으면 409 CUSTOMER_PHONE_DUPLICATED,
 * 기존 정보는 바꾸지 않는다 (REG-05).
 */
export function createCustomer(body) {
  const dup = customers.find((c) => phoneKey(c.phone) === phoneKey(body.phone))
  if (dup) {
    return fakeError(409, 'CUSTOMER_PHONE_DUPLICATED', '이미 등록된 고객입니다.', {
      details: { customerId: dup.id, name: dup.name, phoneMasked: dup.phoneMasked },
    })
  }
  if (body.staffId != null && !staff.some((s) => s.id === body.staffId)) {
    return fakeError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', {
      fieldErrors: [{ field: 'staffId', reason: '이 매장의 활성 직원이 아닙니다.' }],
    })
  }

  nextId += 1
  const created = {
    id: nextId,
    name: body.name ?? body.nameEn,
    phone: body.phone,
    phoneMasked: `****-${phoneKey(body.phone).slice(-4)}`,
    grade: 'NORMAL',
    lastVisitAt: null,
    staffName: staff.find((s) => s.id === body.staffId)?.name ?? null,
    members: [],
  }
  customers.push(created)
  return fakeResponse({ id: created.id, name: created.name, phoneMasked: created.phoneMasked }, 300)
}

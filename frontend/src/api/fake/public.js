import { fakeResponse, fakeError } from './fake'
import { sizeOptions, customers } from '@/mocks/data'
import { stores } from '@/mocks/sales'

/** GET /public/sizes : 품목·대상별 허용 사이즈 (03 4.6 아래) */
export function getSizes() {
  return fakeResponse(sizeOptions)
}

/** GET /public/join-info?store=: 매장 이름·동의 문구·사이즈 (없는 매장 코드면 404) */
export function getJoinInfo(storeCode) {
  const store = stores.find((s) => s.code === storeCode)
  if (!store) return fakeError(404, 'STORE_NOT_FOUND', 'QR 코드가 올바르지 않습니다.')
  return fakeResponse({
    store: { code: store.code, name: store.name },
    consents: {
      privacy:
        '[개인정보 수집·이용 동의 (필수)]\n· 수집 목적: 단골 관리, 구매 이력·등급 할인 안내, 사이즈 상담, 연락\n· 동의하지 않으면 등록과 등급 할인을 받을 수 없습니다.',
      age14: '만 14세 이상입니다. (필수)',
      marketing:
        '[마케팅 수신 동의 (선택)]\n신상품·행사 소식을 문자 또는 카카오톡으로 받는 것에 동의합니다. 동의하지 않아도 등록과 등급 할인은 받을 수 있습니다.',
    },
    sizes: sizeOptions,
  })
}

/**
 * POST /public/registrations · POST /kiosk/registrations (03 4.5).
 * 신규든 기존 번호든 항상 같은 응답 (REG-05). 기존 번호면 아무것도 바꾸지 않는다.
 */
export function publicRegister(body) {
  const key = (p) => String(p).replace(/[^\d+]/g, '')
  if (!customers.some((c) => key(c.phone) === key(body.phone))) {
    const id = Math.max(...customers.map((c) => c.id)) + 1
    customers.push({
      id,
      name: body.name ?? body.nameEn,
      phone: body.phone,
      phoneMasked: `****-${key(body.phone).slice(-4)}`,
      grade: 'NORMAL',
      lastVisitAt: null,
      staffName: null,
      members: [],
    })
  }
  return fakeResponse({ message: '등록이 완료되었습니다.' }, 400)
}

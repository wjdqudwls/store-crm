import { http } from './http'

/**
 * 공개(키오스크·QR) 정보 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/public.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/public') : null

export const getSizes = () => (fake ? fake().then((m) => m.getSizes()) : http.get('/public/sizes'))

/** GET /public/join-info?store=&staff= → { store, consents, sizes }. 로그인 없이 연다 (03 4.5) */
export const getJoinInfo = (store, staff) =>
  fake
    ? fake().then((m) => m.getJoinInfo(store))
    : http.get('/public/join-info', { params: staff ? { store, staff } : { store } })

/** POST /public/registrations (QR). IP당 분당 5번, 넘으면 429 TOO_MANY_REQUESTS. 기존 번호도 같은 200 */
export const publicRegister = (body) =>
  fake ? fake().then((m) => m.publicRegister(body)) : http.post('/public/registrations', body)

/** POST /kiosk/registrations (매장 iPad, KIOSK 계정). 매장은 토큰에서 정한다 */
export const kioskRegister = (body) =>
  fake ? fake().then((m) => m.publicRegister(body)) : http.post('/kiosk/registrations', body)

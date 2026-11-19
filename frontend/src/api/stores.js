import { http, notConnected } from './http'

/**
 * 매장·직원 계정 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/stores.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/stores') : null

/** GET /stores (본사만, 03 3.7). 매장 상세 `GET /stores/{id}`는 서버에 아직 없다 */
export const getStores = () => (fake ? fake().then((m) => m.getStores()) : http.get('/stores'))

export const getStore = (...args) =>
  fake ? fake().then((m) => m.getStore(...args)) : notConnected('getStore')

/** GET /stores/{storeId}/staff: 본사는 매장 계정·직원·키오스크 모두 (03 3.7) */
export const getStoreAccounts = (storeId) =>
  fake ? fake().then((m) => m.getStoreAccounts(storeId)) : http.get(`/stores/${storeId}/staff`)

export const createAccount = (...args) =>
  fake ? fake().then((m) => m.createAccount(...args)) : notConnected('createAccount')

export const handoverCandidates = (...args) =>
  fake ? fake().then((m) => m.handoverCandidates(...args)) : notConnected('handoverCandidates')

export const deactivateAccount = (...args) =>
  fake ? fake().then((m) => m.deactivateAccount(...args)) : notConnected('deactivateAccount')

export const resetPassword = (...args) =>
  fake ? fake().then((m) => m.resetPassword(...args)) : notConnected('resetPassword')

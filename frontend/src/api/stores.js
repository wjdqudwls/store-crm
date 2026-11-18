import { notConnected } from './http'

/**
 * 매장·직원 계정 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/stores.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/stores') : null

export const getStores = (...args) =>
  fake ? fake().then((m) => m.getStores(...args)) : notConnected('getStores')

export const getStore = (...args) =>
  fake ? fake().then((m) => m.getStore(...args)) : notConnected('getStore')

export const getStoreAccounts = (...args) =>
  fake ? fake().then((m) => m.getStoreAccounts(...args)) : notConnected('getStoreAccounts')

export const createAccount = (...args) =>
  fake ? fake().then((m) => m.createAccount(...args)) : notConnected('createAccount')

export const handoverCandidates = (...args) =>
  fake ? fake().then((m) => m.handoverCandidates(...args)) : notConnected('handoverCandidates')

export const deactivateAccount = (...args) =>
  fake ? fake().then((m) => m.deactivateAccount(...args)) : notConnected('deactivateAccount')

export const resetPassword = (...args) =>
  fake ? fake().then((m) => m.resetPassword(...args)) : notConnected('resetPassword')

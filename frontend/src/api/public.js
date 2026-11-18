import { http, notConnected } from './http'

/**
 * 공개(키오스크·QR) 정보 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/public.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/public') : null

export const getSizes = () => (fake ? fake().then((m) => m.getSizes()) : http.get('/public/sizes'))

export const getJoinInfo = (...args) =>
  fake ? fake().then((m) => m.getJoinInfo(...args)) : notConnected('getJoinInfo')

export const publicRegister = (...args) =>
  fake ? fake().then((m) => m.publicRegister(...args)) : notConnected('publicRegister')

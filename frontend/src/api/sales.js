import { notConnected } from './http'

/**
 * 판매·반품·마감 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/sales.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/sales') : null

export const getStaff = (...args) =>
  fake ? fake().then((m) => m.getStaff(...args)) : notConnected('getStaff')

/** GET /sales/daily-summary는 매장별 배열이다 (03 4.12). 화면은 내 매장 1개만 쓰므로 첫 항목을 준다 */
export const getDailySummary = (...args) =>
  (fake ? fake().then((m) => m.getDailySummary(...args)) : notConnected('getDailySummary')).then(
    (rows) => rows[0],
  )

export const getDailySummaries = (...args) =>
  fake ? fake().then((m) => m.getDailySummaries(...args)) : notConnected('getDailySummaries')

export const getCustomerForSale = (...args) =>
  fake ? fake().then((m) => m.getCustomerForSale(...args)) : notConnected('getCustomerForSale')

export const searchProducts = (...args) =>
  fake ? fake().then((m) => m.searchProducts(...args)) : notConnected('searchProducts')

export const createSale = (...args) =>
  fake ? fake().then((m) => m.createSale(...args)) : notConnected('createSale')

export const getSales = (...args) =>
  fake ? fake().then((m) => m.getSales(...args)) : notConnected('getSales')

export const getSale = (...args) =>
  fake ? fake().then((m) => m.getSale(...args)) : notConnected('getSale')

export const returnSale = (...args) =>
  fake ? fake().then((m) => m.returnSale(...args)) : notConnected('returnSale')

export const exchangeSale = (...args) =>
  fake ? fake().then((m) => m.exchangeSale(...args)) : notConnected('exchangeSale')

export const correctSale = (...args) =>
  fake ? fake().then((m) => m.correctSale(...args)) : notConnected('correctSale')

export const voidSale = (...args) =>
  fake ? fake().then((m) => m.voidSale(...args)) : notConnected('voidSale')

export const closeDay = (...args) =>
  fake ? fake().then((m) => m.closeDay(...args)) : notConnected('closeDay')

export const getDayCloses = (...args) =>
  fake ? fake().then((m) => m.getDayCloses(...args)) : notConnected('getDayCloses')

export const reopenDay = (...args) =>
  fake ? fake().then((m) => m.reopenDay(...args)) : notConnected('reopenDay')

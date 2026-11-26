import { http } from './http'
import { getCustomer, getMembers } from './customers'
import { useSessionStore } from '@/stores/session'

/**
 * 판매·반품·마감 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/sales.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/sales') : null

/** 키를 보내는 요청 헤더 (03 4.1: 판매·반품·기록 취소는 Idempotency-Key, 화면이 창을 열 때 만든 UUID) */
const keyed = (key) => (key ? { headers: { 'Idempotency-Key': key } } : {})

/** 매장의 활성 직원 (판매 사원·처리 직원 고르기). 03 3.7: 매장 계정은 활성 직원만 받는다 */
export const getStaff = (storeId) =>
  fake
    ? fake().then((m) => m.getStaff())
    : http.get(`/stores/${storeId ?? useSessionStore().me.storeId}/staff`)

/** GET /sales/daily-summary는 매장별 배열이다 (03 4.12). 화면은 내 매장 1개만 쓰므로 첫 항목을 준다 */
export const getDailySummary = (storeId) =>
  (fake
    ? fake().then((m) => m.getDailySummary(storeId))
    : http.get('/sales/daily-summary', { params: storeId ? { storeId } : {} })
  ).then((rows) => rows[0])

/** 본사 매장 목록용: 모든 매장의 오늘 요약 */
export const getDailySummaries = () =>
  fake ? fake().then((m) => m.getDailySummaries()) : http.get('/sales/daily-summary')

/**
 * 판매 기록·반품 화면이 쓰는 고객 정보(등급·할인율·가족). GET /customers/{id}에서 필요한 칸만 뽑는다.
 * 가족(착용자)은 구성원 API가 연결되기 전까지 빈 목록이다.
 */
export const getCustomerForSale = async (id) => {
  if (fake) return fake().then((m) => m.getCustomerForSale(id))
  const [c, members] = await Promise.all([getCustomer(id), getMembers(id).catch(() => [])])
  return {
    id: c.id,
    name: c.name,
    phoneMasked: `****-${String(c.phone).replace(/\D/g, '').slice(-4)}`,
    grade: { current: c.grade.current, discountRate: c.grade.discountRate },
    members,
  }
}

/** 상품 검색 (판매 기록에서 고르기) */
export const searchProducts = (keyword) =>
  fake
    ? fake().then((m) => m.searchProducts(keyword))
    : http.get('/products', { params: { keyword } })

export const createSale = (body, key) =>
  fake ? fake().then((m) => m.createSale(body, key)) : http.post('/sales', body, keyed(key))

export const getSales = ({ from, to, storeId } = {}) =>
  fake
    ? fake().then((m) => m.getSales({ from, to, storeId }))
    : http.get('/sales', { params: { from, to, storeId, size: 100 } })

export const getSale = (id) => (fake ? fake().then((m) => m.getSale(id)) : http.get(`/sales/${id}`))

export const returnSale = (id, body, key) =>
  fake
    ? fake().then((m) => m.returnSale(id, body, key))
    : http.post(`/sales/${id}/returns`, body, keyed(key))

/** 교환: 돌려받은 줄을 반품하고 새 영수증을 만든다 (03 4.8) */
export const exchangeSale = (id, body, key) =>
  fake
    ? fake().then((m) => m.exchangeSale(id, body, key))
    : http.post(`/sales/${id}/exchanges`, body, keyed(key))

/** 정정: 원 판매는 기록 취소되고 새 판매가 만들어진다 (03 4.9) */
export const correctSale = (id, body, key) =>
  fake
    ? fake().then((m) => m.correctSale(id, body, key))
    : http.put(`/sales/${id}`, body, keyed(key))

export const voidSale = (id, body, key) =>
  fake
    ? fake().then((m) => m.voidSale(id, body, key))
    : http.post(`/sales/${id}/void`, body, keyed(key))

export const closeDay = () => (fake ? fake().then((m) => m.closeDay()) : http.post('/day-closes'))

export const getDayCloses = (storeId) =>
  fake ? fake().then((m) => m.getDayCloses(storeId)) : http.get(`/stores/${storeId}/day-closes`)

export const reopenDay = (storeId, date) =>
  fake
    ? fake().then((m) => m.reopenDay(storeId, date))
    : http.delete(`/stores/${storeId}/day-closes/${date}`)

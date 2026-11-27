import { http, notConnected } from './http'
import { callListParams } from '@/features/customer/callList'

/**
 * 고객·가족·메모·후속 연락 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/customers.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/customers') : null

export const createCustomer = (body) =>
  fake ? fake().then((m) => m.createCustomer(body)) : http.post('/customers', body)

export const updateCustomer = (id, body) =>
  fake ? fake().then((m) => m.updateCustomer(id, body)) : http.patch(`/customers/${id}`, body)

export const searchCustomers = (name, page = 0, size = 20) =>
  fake
    ? fake().then((m) => m.searchCustomers(name))
    : http.get('/customers', { params: { name, page, size } })

export const getCustomer = (id, { includeAddress = false } = {}) =>
  fake
    ? fake().then((m) => m.getCustomer(id, { includeAddress }))
    : http.get(`/customers/${id}`, { params: includeAddress ? { include: 'address' } : {} })

/** GET /customers/{id}/purchases?keyword= (03 4.12). 기록 취소된 판매는 서버가 뺀다 */
export const getPurchases = (id, keyword = '') =>
  fake
    ? fake().then((m) => m.getPurchases(id, keyword))
    : http.get(`/customers/${id}/purchases`, { params: { keyword, size: 100 } })

export const getGradeHistory = (...args) =>
  fake ? fake().then((m) => m.getGradeHistory(...args)) : notConnected('getGradeHistory')

export const getMembers = (id) =>
  fake ? fake().then((m) => m.getMembers(id)) : http.get(`/customers/${id}/members`)

export const createMember = (id, body) =>
  fake ? fake().then((m) => m.createMember(id, body)) : http.post(`/customers/${id}/members`, body)

export const updateMember = (id, body) =>
  fake ? fake().then((m) => m.updateMember(id, body)) : http.patch(`/members/${id}`, body)

export const deleteMember = (id) =>
  fake ? fake().then((m) => m.deleteMember(id)) : http.delete(`/members/${id}`)

/** GET /customers/{id}/notes (최신순, 03 4.12) */
export const getNotes = (id) =>
  fake
    ? fake().then((m) => m.getNotes(id))
    : http.get(`/customers/${id}/notes`, { params: { size: 100 } })

/** POST /customers/{id}/notes. 매장 계정은 staffId 필수, 방문 예약·재연락 날짜(reminder)는 서버가 B4-3 전까지 400으로 거절한다 */
export const createNote = (id, body) =>
  fake ? fake().then((m) => m.createNote(id, body)) : http.post(`/customers/${id}/notes`, body)

/** PATCH /notes/{id} — 내용만 고친다 */
export const updateNote = (noteId, content) =>
  fake
    ? fake().then((m) => m.updateNote(noteId, content))
    : http.patch(`/notes/${noteId}`, { content })

/** DELETE /notes/{id} (204) */
export const deleteNote = (noteId) =>
  fake ? fake().then((m) => m.deleteNote(noteId)) : http.delete(`/notes/${noteId}`)

/** GET /customers/{id}/follow-ups (열린 것 먼저, 만기일 순, 03 4.12) */
export const getFollowUps = (id) =>
  fake
    ? fake().then((m) => m.getFollowUps(id))
    : http.get(`/customers/${id}/follow-ups`, { params: { size: 100 } })

/** POST /follow-ups/{id}/complete — 연락 결과로 완료. 본문은 연락 메모와 같다(staffId·callResult·channel·content) */
export const completeFollowUp = (id, body) =>
  fake
    ? fake().then((m) => m.completeFollowUp(id, body))
    : http.post(`/follow-ups/${id}/complete`, body)

/** POST /follow-ups/{id}/visited — 방문 예약 할 일을 방문함으로 완료 */
export const completeVisit = (id) =>
  fake ? fake().then((m) => m.completeVisit(id)) : http.post(`/follow-ups/${id}/visited`)

/** POST /follow-ups/{id}/skip — 기록 없이 건너뛰기 */
export const skipFollowUp = (id) =>
  fake ? fake().then((m) => m.skipFollowUp(id)) : http.post(`/follow-ups/${id}/skip`)

/** GET /follow-ups/today — 만기일이 오늘이거나 지난 열린 할 일 (직원은 내 할 일, 매장 계정은 매장 전체) */
export const getToday = () =>
  fake ? fake().then((m) => m.getToday()) : http.get('/follow-ups/today', { params: { size: 100 } })

/** GET /call-list — 마지막 방문이 오래된 순 (03 3.3). 비운 조건은 서버 기본값이 되살아나지 않게 끄는 값으로 보낸다 */
export const getCallList = (filters = {}) =>
  fake
    ? fake().then((m) => m.getCallList(filters))
    : http.get('/call-list', { params: callListParams(filters) })

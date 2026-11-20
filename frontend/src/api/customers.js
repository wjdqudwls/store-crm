import { http, notConnected } from './http'

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

export const getMembers = (...args) =>
  fake ? fake().then((m) => m.getMembers(...args)) : notConnected('getMembers')

export const createMember = (...args) =>
  fake ? fake().then((m) => m.createMember(...args)) : notConnected('createMember')

export const updateMember = (...args) =>
  fake ? fake().then((m) => m.updateMember(...args)) : notConnected('updateMember')

export const deleteMember = (...args) =>
  fake ? fake().then((m) => m.deleteMember(...args)) : notConnected('deleteMember')

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

export const getFollowUps = (...args) =>
  fake ? fake().then((m) => m.getFollowUps(...args)) : notConnected('getFollowUps')

export const completeFollowUp = (...args) =>
  fake ? fake().then((m) => m.completeFollowUp(...args)) : notConnected('completeFollowUp')

export const completeVisit = (...args) =>
  fake ? fake().then((m) => m.completeVisit(...args)) : notConnected('completeVisit')

export const skipFollowUp = (...args) =>
  fake ? fake().then((m) => m.skipFollowUp(...args)) : notConnected('skipFollowUp')

export const getToday = (...args) =>
  fake ? fake().then((m) => m.getToday(...args)) : notConnected('getToday')

export const getCallList = (...args) =>
  fake ? fake().then((m) => m.getCallList(...args)) : notConnected('getCallList')

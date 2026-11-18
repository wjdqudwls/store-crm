import { notConnected } from './http'

/**
 * 고객·가족·메모·후속 연락 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/customers.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/customers') : null

export const createCustomer = (...args) =>
  fake ? fake().then((m) => m.createCustomer(...args)) : notConnected('createCustomer')

export const updateCustomer = (...args) =>
  fake ? fake().then((m) => m.updateCustomer(...args)) : notConnected('updateCustomer')

export const searchCustomers = (...args) =>
  fake ? fake().then((m) => m.searchCustomers(...args)) : notConnected('searchCustomers')

export const getCustomer = (...args) =>
  fake ? fake().then((m) => m.getCustomer(...args)) : notConnected('getCustomer')

export const getPurchases = (...args) =>
  fake ? fake().then((m) => m.getPurchases(...args)) : notConnected('getPurchases')

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

export const getNotes = (...args) =>
  fake ? fake().then((m) => m.getNotes(...args)) : notConnected('getNotes')

export const createNote = (...args) =>
  fake ? fake().then((m) => m.createNote(...args)) : notConnected('createNote')

export const updateNote = (...args) =>
  fake ? fake().then((m) => m.updateNote(...args)) : notConnected('updateNote')

export const deleteNote = (...args) =>
  fake ? fake().then((m) => m.deleteNote(...args)) : notConnected('deleteNote')

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

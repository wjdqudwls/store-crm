import { notConnected } from './http'

/**
 * 알림함·알림 설정 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/notifications.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/notifications') : null

export const getNotifications = (...args) =>
  fake ? fake().then((m) => m.getNotifications(...args)) : notConnected('getNotifications')

export const markRead = (...args) =>
  fake ? fake().then((m) => m.markRead(...args)) : notConnected('markRead')

export const getNotifySetting = (...args) =>
  fake ? fake().then((m) => m.getNotifySetting(...args)) : notConnected('getNotifySetting')

export const saveNotifySetting = (...args) =>
  fake ? fake().then((m) => m.saveNotifySetting(...args)) : notConnected('saveNotifySetting')

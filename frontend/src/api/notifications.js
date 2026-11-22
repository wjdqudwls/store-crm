import { http } from './http'

/**
 * 알림함·알림 설정 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/notifications.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/notifications') : null

/** GET /notifications — 안 읽은 것 먼저, 그 안에서 최신순. `unreadCount`는 배지 숫자 (직원 개인 계정만, 03 4.12) */
export const getNotifications = () =>
  fake
    ? fake().then((m) => m.getNotifications())
    : http.get('/notifications', { params: { size: 100 } })

/** POST /notifications/{id}/read */
export const markRead = (id) =>
  fake ? fake().then((m) => m.markRead(id)) : http.post(`/notifications/${id}/read`)

/** GET /me/notification-settings → { timing, push } (push는 이 계정의 푸시 구독 여부) */
export const getNotifySetting = () =>
  fake ? fake().then((m) => m.getNotifySetting()) : http.get('/me/notification-settings')

/**
 * 알림 시점 저장. 실서버는 `PUT /me/notification-settings { timing }`만 보낸다.
 * 푸시 구독은 별도 API(`POST·DELETE /me/push-subscriptions`)이고 아직 화면에서 연결하지 않는다(서비스 워커·VAPID 키 필요).
 */
export const saveNotifySetting = (body) =>
  fake
    ? fake().then((m) => m.saveNotifySetting(body))
    : http.put('/me/notification-settings', { timing: body.timing })

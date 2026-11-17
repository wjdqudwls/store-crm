import { fakeResponse, fakeError } from './fake'
import { notifications, notifySettings, defaultNotifySetting } from '@/mocks/notifications'
import { tasksOfStaff } from './customers'
import { reminderGroups } from '@/features/customer/reminder'
import { useSessionStore } from '@/stores/session'

/** 알림함 API (05 SCR-07, CAL-09·10·11·12). 직원 개인 계정만 받는다 */

const me = () => useSessionStore().me
const mine = () => (notifications[me().id] ??= [])
const readReminders = new Set()

const todayText = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 방문 예약·재연락 묶음 알림. 서버는 매일 아침 9시에 이 묶음을 만들어 알림함에 쌓고 폰 푸시를 보낸다.
 * 가짜 데이터에서는 알림함을 열 때 계산한다.
 */
function reminderNotices() {
  const setting = getSettingSync()
  const today = todayText()
  return reminderGroups(tasksOfStaff(me().id), setting.timing, today).map((g) => {
    const id = `r-${g.kind}-${g.date}-${g.when}`
    return {
      id,
      type: g.kind === 'VISIT' ? 'VISIT' : 'RECALL',
      customerId: g.customerIds.length === 1 ? g.customerIds[0] : null,
      refId: null,
      message: g.message,
      createdAt: `${today}T09:00:00+09:00`,
      read: readReminders.has(id),
    }
  })
}

/** GET /notifications: 안 읽은 것 먼저, 그 안에서 최신순 */
export function getNotifications() {
  const list = [...reminderNotices(), ...mine()].sort(
    (a, b) => a.read - b.read || b.createdAt.localeCompare(a.createdAt),
  )
  return fakeResponse({
    unreadCount: list.filter((n) => !n.read).length,
    content: list,
    page: 0,
    size: 20,
    totalElements: list.length,
    totalPages: 1,
  })
}

/** POST /notifications/{id}/read */
export function markRead(id) {
  if (String(id).startsWith('r-')) {
    readReminders.add(id)
    return fakeResponse(null)
  }
  const n = mine().find((x) => x.id === id)
  if (!n) return fakeError(404, 'NOTIFICATION_NOT_FOUND', '알림을 찾을 수 없습니다.')
  n.read = true
  return fakeResponse(null)
}

function getSettingSync() {
  return (notifySettings[me().id] ??= defaultNotifySetting())
}

/** GET /me/notification-settings */
export function getNotifySetting() {
  return fakeResponse(getSettingSync())
}

/** PUT /me/notification-settings */
export function saveNotifySetting(body) {
  if (!['NONE', 'DAY_BEFORE', 'SAME_DAY', 'BOTH'].includes(body.timing)) {
    return fakeError(400, 'VALIDATION_FAILED', '알림 시점을 골라 주세요.')
  }
  notifySettings[me().id] = { ...getSettingSync(), ...body }
  return fakeResponse(notifySettings[me().id], 250)
}

import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '@/stores/session'
import { createNote, getFollowUps } from './customers'
import { getNotifications, saveNotifySetting } from './notifications'

const ymd = (offset) => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

describe('메모 예약 → 할 일 → 묶음 알림 (CAL-10·12, 가짜 API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useSessionStore().switchAccount(17) // 이지은 · 시니어
  })

  it('should_create_visit_task_for_writer_when_reservation_saved', async () => {
    await createNote(1502, {
      type: 'RESERVATION',
      content: '블라우스 수선 맡기러 옴',
      reminder: { date: ymd(1) },
    })
    const tasks = (await getFollowUps(1502)).content
    const visit = tasks.find((t) => t.kind === 'VISIT' && t.dueDate === ymd(1))
    expect(visit).toMatchObject({ dueDate: ymd(1), staffId: 17, status: 'OPEN' })
  })

  it('should_group_three_visits_tomorrow_into_one_notice', async () => {
    const res = await getNotifications()
    const visit = res.content.find((n) => n.type === 'VISIT')
    // 기본 데이터 2명 + 위에서 예약한 1명
    expect(visit.message).toBe('내일 고객님들이 방문할 예정이에요. (3명)')
    expect(visit.customerId).toBeNull()
  })

  it('should_send_nothing_when_timing_none', async () => {
    await saveNotifySetting({ timing: 'NONE' })
    const res = await getNotifications()
    expect(res.content.some((n) => n.type === 'VISIT')).toBe(false)
  })

  it('should_reject_reservation_without_date', async () => {
    await expect(createNote(1502, { type: 'RESERVATION', content: '방문' })).rejects.toMatchObject({
      status: 400,
    })
  })
})

import { describe, it, expect } from 'vitest'
import { addDays, reminderGroups, reminderMessage } from './reminder'

describe('reminderMessage (2026-11-13 사용자 결정: 1명이면 이름, 2명 이상이면 묶어서)', () => {
  it('should_name_the_customer_when_one_visit', () => {
    expect(reminderMessage('VISIT', 'TOMORROW', ['김하늘'])).toBe('내일 김하늘님 방문 예정이에요.')
  })
  it('should_group_when_two_or_more_visits', () => {
    expect(reminderMessage('VISIT', 'TOMORROW', ['김하늘', '오세진'])).toBe(
      '내일 고객님들이 방문할 예정이에요. (2명)',
    )
  })
  it('should_say_today_when_same_day', () => {
    expect(reminderMessage('VISIT', 'TODAY', ['김하늘'])).toBe('오늘 김하늘님 방문 예정이에요.')
  })
  it('should_word_recall_differently', () => {
    expect(reminderMessage('RECALL', 'TODAY', ['윤가람'])).toBe(
      '오늘 윤가람님께 다시 연락할 날이에요.',
    )
    expect(reminderMessage('RECALL', 'TOMORROW', ['윤가람', '김하늘', '오세진'])).toBe(
      '내일 다시 연락할 고객님이 3명이에요.',
    )
  })
})

describe('addDays', () => {
  it('should_cross_month_end', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01')
  })
})

describe('reminderGroups (알림 시점: 설정 안 함 · 전날 · 당일 아침 · 둘 다)', () => {
  const today = '2026-11-13'
  const task = (id, kind, dueDate, name, status = 'OPEN') => ({
    id,
    kind,
    dueDate,
    customerId: id,
    customerName: name,
    status,
  })
  const tasks = [
    task(1, 'VISIT', '2026-11-14', '김하늘'),
    task(2, 'VISIT', '2026-11-14', '오세진'),
    task(3, 'RECALL', '2026-11-13', '윤가람'),
    task(4, 'VISIT', '2026-11-13', '서다인', 'DONE'),
    task(5, 'SALE', '2026-11-14', '한예린'),
  ]

  it('should_send_nothing_when_none', () => {
    expect(reminderGroups(tasks, 'NONE', today)).toEqual([])
  })
  it('should_group_tomorrow_visits_when_day_before', () => {
    const g = reminderGroups(tasks, 'DAY_BEFORE', today)
    expect(g).toHaveLength(1)
    expect(g[0]).toMatchObject({ kind: 'VISIT', when: 'TOMORROW', names: ['김하늘', '오세진'] })
  })
  it('should_include_only_open_today_tasks_when_same_day', () => {
    const g = reminderGroups(tasks, 'SAME_DAY', today)
    expect(g.map((x) => [x.kind, x.when, x.names])).toEqual([['RECALL', 'TODAY', ['윤가람']]])
  })
  it('should_send_both_when_both', () => {
    expect(reminderGroups(tasks, 'BOTH', today)).toHaveLength(2)
  })
})

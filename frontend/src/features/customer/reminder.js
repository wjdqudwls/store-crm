/**
 * 방문 예약·재연락 알림 (2026-11-13 사용자 결정, 01 CAL-10~12).
 * 메모에서 날짜를 정하면 할 일(VISIT·RECALL)이 생기고, 받는 직원의 알림 시점 설정대로
 * 전날·당일 아침에 묶음 알림을 보낸다. 실제 보내기는 서버 스케줄러(B4)가 하고, 이 함수는 그 규칙이다.
 */

export const timingLabels = {
  NONE: '설정 안 함',
  DAY_BEFORE: '전날',
  SAME_DAY: '당일 아침',
  BOTH: '둘 다',
}

/** 할 일 종류: 판매 후속 연락 · 방문 예약 · 다시 연락 */
export const taskKindLabels = { SALE: '후속 연락', VISIT: '방문 예정', RECALL: '다시 연락' }

/** `2026-11-30` + 1 → `2026-12-01` */
export function addDays(ymd, n) {
  const [y, m, d] = ymd.split('-').map(Number)
  const t = new Date(Date.UTC(y, m - 1, d + n))
  return t.toISOString().slice(0, 10)
}

/** 알림 문구. 1명이면 이름, 2명 이상이면 묶는다 */
export function reminderMessage(kind, when, names) {
  const day = when === 'TOMORROW' ? '내일' : '오늘'
  const n = names.length
  if (kind === 'VISIT') {
    return n === 1
      ? `${day} ${names[0]}님 방문 예정이에요.`
      : `${day} 고객님들이 방문할 예정이에요. (${n}명)`
  }
  return n === 1
    ? `${day} ${names[0]}님께 다시 연락할 날이에요.`
    : `${day} 다시 연락할 고객님이 ${n}명이에요.`
}

/**
 * 오늘 보낼 알림 묶음. `OPEN`인 VISIT·RECALL 할 일만, 종류 × (내일/오늘)마다 하나.
 * @param {{ kind: string, dueDate: string, status: string, customerId: number, customerName: string }[]} tasks 받는 직원의 할 일
 * @param {'NONE'|'DAY_BEFORE'|'SAME_DAY'|'BOTH'} timing
 * @param {string} today `2026-11-13`
 */
export function reminderGroups(tasks, timing, today) {
  const whens = []
  if (timing === 'DAY_BEFORE' || timing === 'BOTH') whens.push(['TOMORROW', addDays(today, 1)])
  if (timing === 'SAME_DAY' || timing === 'BOTH') whens.push(['TODAY', today])
  const groups = []
  for (const kind of ['VISIT', 'RECALL']) {
    for (const [when, date] of whens) {
      const hit = tasks.filter((t) => t.kind === kind && t.status === 'OPEN' && t.dueDate === date)
      if (!hit.length) continue
      groups.push({
        kind,
        when,
        date,
        names: hit.map((t) => t.customerName),
        customerIds: hit.map((t) => t.customerId),
        message: reminderMessage(
          kind,
          when,
          hit.map((t) => t.customerName),
        ),
      })
    }
  }
  return groups
}

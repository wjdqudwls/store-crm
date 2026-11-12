/**
 * 고객 검색·상세 화면의 표시 규칙 (05 SCR-06, 1.4).
 */

const DAY = 86400000

/** 한국 시간 기준 그날 0시 (브라우저가 한국 시간이라고 본다) */
function startOfDay(d) {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

/** 마지막 방문 `28일 전`, 오늘이면 `오늘` */
export function daysAgoLabel(iso, now = new Date()) {
  if (!iso) return '-'
  const days = Math.round((startOfDay(now) - startOfDay(iso)) / DAY)
  return days <= 0 ? '오늘' : `${days}일 전`
}

const pad = (n) => String(n).padStart(2, '0')

/** 목록 `10/15 14:03`, 상세 `2026-10-15 14:03`, 오늘이면 `오늘 14:03` */
export function dateTimeLabel(iso, now = new Date(), full = false) {
  const d = new Date(iso)
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (startOfDay(d).getTime() === startOfDay(now).getTime()) return `오늘 ${time}`
  if (full) return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${time}`
  return `${d.getMonth() + 1}/${d.getDate()} ${time}`
}

/** `2026-10-15` */
export function dateLabel(iso) {
  const d = new Date(iso)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 요약 칸의 큰 금액: 만원 아래는 버린다 (`342만원`) */
export function manwon(n) {
  if (n < 10000) return `${n.toLocaleString('ko-KR')}원`
  return `${Math.floor(n / 10000).toLocaleString('ko-KR')}만원`
}

/** 홍보 연락 가능: 문자·카톡 중 하나라도 동의 (BR-08) */
export function marketingAllowed(marketing) {
  return Boolean(marketing?.sms || marketing?.kakao)
}

export const pcTabs = ['purchases', 'notes', 'followUps', 'family', 'profile']
export const phoneTabs = ['summary', ...pcTabs]

/**
 * 처음 보이는 탭 (05 D4). `asked`는 주소의 `?tab=`.
 * 오늘 할 일·콜리스트·알림 → 메모, 판매 기록·영수증 → 구매이력, 고객 검색 → 폰 요약·PC 구매이력.
 */
export function firstTab(from, isPhone, asked) {
  const tabs = isPhone ? phoneTabs : pcTabs
  if (asked && tabs.includes(asked)) return asked
  if (['today', 'call-list', 'notifications'].includes(from)) return 'notes'
  if (['sale', 'receipt'].includes(from)) return 'purchases'
  return isPhone ? 'summary' : 'purchases'
}

const order = ['NORMAL', 'D', 'C', 'B', 'A']
const rank = (g) => order.indexOf(g)

/** 등급 근거 아래 안내 (GRD-03, BR-05) */
export function gradeNotes(grade) {
  const notes = ['둘 중 높은 등급을 적용합니다']
  if (rank(grade.computed) < rank(grade.confirmed)) {
    notes.push('다음 정기 산정(6월 1일)에 실적 기준으로 다시 정해집니다')
  }
  return notes
}

export const gradeReasonLabels = {
  PROMOTION: '즉시 승급',
  PERIODIC: '정기 산정',
  PROVISIONAL_RECALC: '임시 재계산',
  REVOKE: '승급 취소',
}

export const noteTypeLabels = { CONSULT: '상담', PURCHASE: '구매', CALL: '연락', ETC: '기타' }
export const callResultLabels = { COMPLETED: '통화 완료', ABSENT: '부재', RECALL: '재연락' }
export const channelLabels = { CALL: '전화', SMS: '문자', KAKAO: '카톡' }
export const followUpStatusLabels = {
  OPEN: '할 일',
  DONE: '완료',
  SKIPPED: '건너뜀',
  CANCELLED: '취소',
  REPLACED: '새 구매로 바뀜',
}
export const relationLabels = { SPOUSE: '배우자', CHILD: '자녀', PARENT: '부모', OTHER: '기타' }
export const genderLabels = { F: '여성', M: '남성' }

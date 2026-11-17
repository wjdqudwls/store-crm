import { clone, fakeResponse, fakeError } from './fake'
import { gradePolicies, gradeDiscounts, followUpRules } from '@/mocks/policy'

/** 설정 API (05 SCR-09, GRD-04·05, CAL-07). 가짜 데이터 */

/** 다음 6월 1일 (6월이면 올해 6월 1일 = 지금 바로) */
export function nextJuneFirst(now = new Date()) {
  const y =
    now.getMonth() === 5
      ? now.getFullYear()
      : now.getMonth() > 5
        ? now.getFullYear() + 1
        : now.getFullYear()
  return `${y}-06-01`
}

/** GET /grade-policy: 지금 기준 + 예약된 다음 기준 */
export function getGradePolicy() {
  const today = new Date().toISOString().slice(0, 10)
  const sorted = [...gradePolicies].sort((a, b) => b.appliesFrom.localeCompare(a.appliesFrom))
  const current = sorted.find((p) => p.appliesFrom <= today)
  const next = sorted.find((p) => p.appliesFrom > today) ?? null
  return fakeResponse({
    ...current,
    discounts: Object.entries(gradeDiscounts).map(([grade, rate]) => ({ grade, rate })),
    next: next && { version: next.version, appliesFrom: next.appliesFrom },
    versions: sorted.map((p) => ({ version: p.version, appliesFrom: p.appliesFrom })),
  })
}

const ascending = (list, key) => list.every((r, i) => i === 0 || r[key] > list[i - 1][key])
const validInts = (list) =>
  list.every((r) =>
    Object.values(r).every((v) => typeof v === 'string' || (Number.isInteger(v) && v >= 0)),
  )

/** POST /grade-policy: 새 기준 (구간·컷은 오름차순, 0 이상 정수) */
export function createGradePolicy(body) {
  const ok =
    ascending(body.amountRules, 'minValue') &&
    ascending(body.ticketRules, 'minValue') &&
    ascending(body.gradeCuts, 'minScore') &&
    validInts([...body.amountRules, ...body.ticketRules, ...body.gradeCuts])
  if (!ok) {
    return fakeError(
      400,
      'VALIDATION_FAILED',
      '구간과 컷은 0 이상 정수이고 위에서 아래로 커져야 합니다.',
    )
  }
  const appliesFrom = nextJuneFirst()
  const version = Math.max(...gradePolicies.map((p) => p.version)) + 1
  const i = gradePolicies.findIndex((p) => p.appliesFrom === appliesFrom)
  const policy = { version, appliesFrom, periodMonths: 12, ...clone(body) }
  if (i >= 0) gradePolicies.splice(i, 1, policy)
  else gradePolicies.push(policy)
  return fakeResponse(policy, 300)
}

/** PUT /grade-discounts: 바로 적용 */
export function saveDiscounts(list) {
  if (!list.every((d) => Number.isInteger(d.rate) && d.rate >= 0 && d.rate <= 100)) {
    return fakeError(400, 'VALIDATION_FAILED', '할인율은 0~100 정수입니다.')
  }
  for (const d of list) gradeDiscounts[d.grade] = d.rate
  return fakeResponse(null, 300)
}

/** POST /grade-policy/periodic-runs: 정기 산정 수동 실행 (데모, 몇 번 실행해도 결과가 같다) */
let runs = 0
export function runPeriodic() {
  runs += 1
  return fakeResponse(
    {
      season: '25/26',
      nextSeason: '26/27',
      policyVersion: 2,
      customerCount: 5000,
      changedCount: runs === 1 ? 412 : 0,
      historyCreated: runs === 1 ? 5000 : 0,
      elapsedMs: 8400,
    },
    900,
  )
}

/** GET /follow-up-rules */
export function getFollowUpRules() {
  return fakeResponse(followUpRules)
}

/** PUT /follow-up-rules: 이후 구매부터 적용 */
export function saveFollowUpRules(list) {
  if (list.some((r) => !r.name.trim() || !Number.isInteger(r.amount) || r.amount < 1)) {
    return fakeError(400, 'VALIDATION_FAILED', '단계 이름과 1 이상의 기간을 넣어 주세요.')
  }
  followUpRules.splice(0, followUpRules.length, ...clone(list))
  return fakeResponse(null, 300)
}

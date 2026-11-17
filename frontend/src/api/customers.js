import { fakeResponse, fakeError } from './fake'
import { customers, staff, gradeDiscounts } from '@/mocks/data'
import {
  customerDetails,
  purchases,
  gradeHistory,
  notes,
  followUps,
  members,
} from '@/mocks/customerDetail'
import { useSessionStore } from '@/stores/session'

/**
 * 고객 API (지금은 가짜 데이터). 12단계에서 axios로 바꾼다 (ADR-032).
 */

let nextId = 2400

/** 비교용: 숫자와 + 만 남긴다. 내국인 010-… 은 그대로 비교 (서버는 E.164 해시로 비교, NFR-04) */
const phoneKey = (p) => String(p).replace(/[^\d+]/g, '')

/**
 * POST /customers (03 4.5 본문 + staffId). 같은 번호가 있으면 409 CUSTOMER_PHONE_DUPLICATED,
 * 기존 정보는 바꾸지 않는다 (REG-05).
 */
export function createCustomer(body) {
  const dup = customers.find((c) => phoneKey(c.phone) === phoneKey(body.phone))
  if (dup) {
    return fakeError(409, 'CUSTOMER_PHONE_DUPLICATED', '이미 등록된 고객입니다.', {
      details: { customerId: dup.id, name: dup.name, phoneMasked: dup.phoneMasked },
    })
  }
  if (body.staffId != null && !staff.some((s) => s.id === body.staffId)) {
    return fakeError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', {
      fieldErrors: [{ field: 'staffId', reason: '이 매장의 활성 직원이 아닙니다.' }],
    })
  }

  nextId += 1
  const created = {
    id: nextId,
    name: body.name ?? body.nameEn,
    phone: body.phone,
    phoneMasked: `****-${phoneKey(body.phone).slice(-4)}`,
    grade: 'NORMAL',
    lastVisitAt: null,
    staffName: staff.find((s) => s.id === body.staffId)?.name ?? null,
    members: [],
  }
  customers.push(created)
  customerDetails[created.id] = {
    nameEn: body.nameEn ?? null,
    nationality: body.nationality,
    createdAt: new Date().toISOString(),
    staff: body.staffId != null ? { id: body.staffId, name: created.staffName } : null,
    profile: {
      gender: body.gender ?? null,
      birthYear: body.birthYear ?? null,
      birthMd: body.birthMd ?? null,
      regionSido: body.address?.sido ?? null,
      regionGu: body.address?.gu ?? null,
      preferredItemTypes: body.preferredItemTypes ?? [],
      sizes: body.sizes ?? {},
      contactChannel: body.contactChannel ?? null,
    },
    addressDetail: body.address?.detail ?? null,
    marketing: { sms: body.agreements.marketingSms, kakao: body.agreements.marketingKakao },
  }
  return fakeResponse({ id: created.id, name: created.name, phoneMasked: created.phoneMasked }, 300)
}

// ---- 고객 검색·상세 (05 SCR-06) ----

/** 지금 로그인한 계정. 실제 API는 토큰으로 안다 */
const me = () => useSessionStore().me
const isEmployee = () => !['STORE', 'HQ', 'KIOSK'].includes(me().role)
const page = (content) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
})

/** GET /customers?name= (03 4.3). 목록에는 전체 번호·가족을 넣지 않는다 (NFR-05) */
export function searchCustomers(name) {
  const q = name.trim()
  const content = customers
    .filter((c) => q && c.name.includes(q))
    .map(({ id, name, phoneMasked, grade, lastVisitAt, staffName }) => ({
      id,
      name,
      phoneMasked,
      grade,
      lastVisitAt,
      staffName,
    }))
  return fakeResponse(page(content))
}

const emptyGrade = {
  current: 'NORMAL',
  computed: 'NORMAL',
  confirmed: 'NORMAL',
  confirmedStatus: 'CONFIRMED',
  season: '26/27',
  seasonTicketCount: 0,
  ticketScore: 0,
  seasonNetAmount: 0,
  amountScore: 0,
  totalScore: 0,
  discountRate: 0,
  policyVersion: 2,
  confirmedBasis: { season: '25/26', ticketCount: 0, netAmount: 0, totalScore: 0 },
}

/**
 * GET /customers/{id} (03 4.4). 상세주소는 `include=address`일 때만 (BR-08).
 * @param {number} id
 * @param {{ includeAddress?: boolean }} [opts]
 */
export function getCustomer(id, { includeAddress = false } = {}) {
  const c = customers.find((x) => x.id === id)
  if (!c) return fakeError(404, 'CUSTOMER_NOT_FOUND', '고객을 찾을 수 없습니다.')
  const d = customerDetails[id] ?? {}
  const res = {
    id: c.id,
    name: c.name,
    nameEn: d.nameEn ?? null,
    nationality: d.nationality ?? 'DOMESTIC',
    phone: c.phone,
    createdAt: d.createdAt ?? null,
    homeStore: d.homeStore ?? { id: 1, name: '강남점' },
    staff: d.staff ?? null,
    grade: d.grade ?? { ...emptyGrade, discountRate: gradeDiscounts.NORMAL },
    stats: d.stats ?? {
      firstVisitAt: null,
      lastVisitAt: null,
      seasonQty: 0,
      lifetimeAmount: 0,
    },
    profile: {
      gender: null,
      birthYear: null,
      birthMd: null,
      regionSido: null,
      regionGu: null,
      preferredItemTypes: [],
      sizes: {},
      contactChannel: null,
      ...d.profile,
    },
    marketing: d.marketing ?? { sms: false, kakao: false },
  }
  if (includeAddress) res.addressDetail = d.addressDetail ?? null
  return fakeResponse(res)
}

/** GET /customers/{id}/purchases?keyword= (03 4.12) */
export function getPurchases(id, keyword = '') {
  const q = keyword.trim().toLowerCase()
  const list = (purchases[id] ?? [])
    .map((p) => ({
      ...p,
      lines: p.lines.filter(
        (l) =>
          !q ||
          l.productName.toLowerCase().includes(q) ||
          (l.productCode ?? '').toLowerCase().includes(q),
      ),
    }))
    .filter((p) => p.lines.length)
  return fakeResponse(page(list))
}

/** GET /customers/{id}/grade-history */
export function getGradeHistory(id) {
  return fakeResponse(page(gradeHistory[id] ?? []))
}

/** GET /customers/{id}/members */
export function getMembers(id) {
  return fakeResponse(members[id] ?? [])
}

/** 메모를 고치고 지울 수 있는지 (MEM-03): 작성 직원 본인, 그 매장의 매장 계정, 본사 */
function canEdit(note) {
  const role = me().role
  if (role === 'HQ' || role === 'STORE') return true
  return note.staffId === me().id
}

/** GET /customers/{id}/notes (최신순) */
export function getNotes(id) {
  const list = (notes[id] ?? []).map((n) => ({ ...n, editable: canEdit(n) }))
  return fakeResponse(page(list))
}

/**
 * POST /customers/{id}/notes. 매장 계정은 작성 직원 `staffId` 필수, 직원은 본인으로 남는다 (R1).
 * @param {number} id
 * @param {{ type: string, content: string, staffId?: number, callResult?: string, channel?: string }} body
 */
export function createNote(id, body) {
  const author = isEmployee() ? me().id : body.staffId
  if (!author) {
    return fakeError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', {
      fieldErrors: [{ field: 'staffId', reason: '작성 직원을 골라 주세요.' }],
    })
  }
  const note = {
    id: Date.now(),
    type: body.type,
    content: body.content,
    callResult: body.callResult ?? null,
    channel: body.channel ?? null,
    staffId: author,
    staffName: staff.find((s) => s.id === author)?.name ?? me().name,
    saleId: null,
    followUpId: body.followUpId ?? null,
    createdAt: new Date().toISOString(),
    updatedAt: null,
    editable: true,
  }
  notes[id] = [note, ...(notes[id] ?? [])]
  return fakeResponse(note, 250)
}

function findNote(noteId) {
  for (const [cid, list] of Object.entries(notes)) {
    const n = list.find((x) => x.id === noteId)
    if (n) return { cid, n }
  }
  return null
}

/** PATCH /notes/{noteId} */
export function updateNote(noteId, content) {
  const found = findNote(noteId)
  if (!found) return fakeError(404, 'NOTE_NOT_FOUND', '메모를 찾을 수 없습니다.')
  if (!canEdit(found.n))
    return fakeError(403, 'ACCESS_DENIED', '이 계정으로는 할 수 없는 일입니다.')
  found.n.content = content
  found.n.updatedAt = new Date().toISOString()
  return fakeResponse({ ...found.n, editable: true }, 250)
}

/** DELETE /notes/{noteId} */
export function deleteNote(noteId) {
  const found = findNote(noteId)
  if (!found) return fakeError(404, 'NOTE_NOT_FOUND', '메모를 찾을 수 없습니다.')
  if (!canEdit(found.n))
    return fakeError(403, 'ACCESS_DENIED', '이 계정으로는 할 수 없는 일입니다.')
  notes[found.cid] = notes[found.cid].filter((x) => x.id !== noteId)
  return fakeResponse(null, 250)
}

/** GET /customers/{id}/follow-ups (OPEN 먼저, 만기일 순) */
export function getFollowUps(id) {
  const list = [...(followUps[id] ?? [])].sort(
    (a, b) => (a.status !== 'OPEN') - (b.status !== 'OPEN') || a.dueDate.localeCompare(b.dueDate),
  )
  return fakeResponse(page(list))
}

function findTask(taskId) {
  for (const [cid, list] of Object.entries(followUps)) {
    const t = list.find((x) => x.id === taskId)
    if (t) return { cid: Number(cid), t }
  }
  return null
}

function closeTask(taskId, status) {
  const found = findTask(taskId)
  if (!found) return { error: fakeError(404, 'FOLLOW_UP_NOT_FOUND', '할 일을 찾을 수 없습니다.') }
  if (found.t.status !== 'OPEN') {
    return {
      error: fakeError(409, 'FOLLOW_UP_ALREADY_CLOSED', '이미 처리된 할 일입니다.'),
    }
  }
  // 직원은 내 할 일만 (03 3.3)
  if (isEmployee() && found.t.staffId !== me().id) {
    return { error: fakeError(403, 'ACCESS_DENIED', '이 계정으로는 할 수 없는 일입니다.') }
  }
  found.t.status = status
  found.t.closedAt = new Date().toISOString()
  return { found }
}

/** POST /follow-ups/{id}/complete: 연락 메모(CALL)를 만들고 할 일을 완료 (CAL-06·02) */
export async function completeFollowUp(taskId, body) {
  const { found, error } = closeTask(taskId, 'DONE')
  if (error) return error
  await createNote(found.cid, { ...body, type: 'CALL', followUpId: taskId })
  return fakeResponse({ ...found.t }, 250)
}

/** POST /follow-ups/{id}/skip */
export function skipFollowUp(taskId) {
  const { found, error } = closeTask(taskId, 'SKIPPED')
  if (error) return error
  return fakeResponse({ ...found.t }, 250)
}

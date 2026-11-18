import { fakeResponse, fakeError } from './fake'
import { customers, staff, gradeDiscounts } from '@/mocks/data'
import { customerDetails, gradeHistory, notes, followUps, members } from '@/mocks/customerDetail'
import { sales } from '@/mocks/sales'
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

/**
 * PATCH /customers/{id} (CUS-04): 프로필·담당 직원·마케팅 수신. 매장 계정·본사만.
 * 바꾼 번호가 다른 고객 것이면 409 CUSTOMER_PHONE_DUPLICATED (그 고객 이름·뒷자리).
 */
export function updateCustomer(id, body) {
  if (!['STORE', 'HQ'].includes(me().role)) {
    return fakeError(403, 'ACCESS_DENIED', '이 계정으로는 할 수 없는 일입니다.')
  }
  const c = customers.find((x) => x.id === id)
  if (!c) return fakeError(404, 'CUSTOMER_NOT_FOUND', '고객을 찾을 수 없습니다.')
  const dup = customers.find((x) => x.id !== id && phoneKey(x.phone) === phoneKey(body.phone))
  if (dup) {
    return fakeError(409, 'CUSTOMER_PHONE_DUPLICATED', '이미 다른 고객의 번호입니다.', {
      details: { customerId: dup.id, name: dup.name, phoneMasked: dup.phoneMasked },
    })
  }
  const st = staff.find((s) => s.id === body.staffId) ?? null
  Object.assign(c, {
    name: body.name ?? body.nameEn,
    phone: body.phone,
    phoneMasked: `****-${phoneKey(body.phone).slice(-4)}`,
    staffName: st?.name ?? null,
  })
  const d = (customerDetails[id] ??= {})
  Object.assign(d, {
    nationality: body.nationality,
    nameEn: body.nameEn ?? null,
    staff: st ? { id: st.id, name: st.name } : null,
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
    marketing: { ...body.marketing },
  })
  return getCustomer(id)
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
  // 판매 목록에서 이 고객의 영수증만 (기록 취소 제외, 최신순). 실제로는 sales 모듈이 준다 (03 3.3)
  const list = sales
    .filter((s) => s.customerId === id && s.status !== 'VOIDED')
    .map((p) => ({
      saleId: p.id,
      receiptNo: p.receiptNo,
      soldAt: p.soldAt,
      storeId: p.store.id,
      storeName: p.store.name,
      kind: p.kind,
      status: p.status,
      totalAmount: p.totalAmount,
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

const relations = ['SPOUSE', 'CHILD', 'PARENT', 'OTHER']
function checkMember(body) {
  const fieldErrors = []
  if (!body.nickname?.trim())
    fieldErrors.push({ field: 'nickname', reason: '호칭을 입력해 주세요.' })
  if (!relations.includes(body.relation))
    fieldErrors.push({ field: 'relation', reason: '관계를 골라 주세요.' })
  return fieldErrors.length
    ? fakeError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', { fieldErrors })
    : null
}
const storeOnly = () =>
  me().role === 'STORE'
    ? null
    : fakeError(403, 'ACCESS_DENIED', '이 계정으로는 할 수 없는 일입니다.')

/** 판매 기록의 착용자 목록(data.js customers[].members)도 같이 맞춘다 */
function syncWearers(customerId) {
  const c = customers.find((x) => x.id === customerId)
  if (c) c.members = (members[customerId] ?? []).map((m) => ({ id: m.id, label: m.nickname }))
}

/** POST /customers/{id}/members (CUS-05, 매장 계정) */
export function createMember(customerId, body) {
  const err = storeOnly() ?? checkMember(body)
  if (err) return err
  const m = { id: Date.now(), ...body, nickname: body.nickname.trim() }
  members[customerId] = [...(members[customerId] ?? []), m]
  syncWearers(customerId)
  return fakeResponse(m, 250)
}

function findMember(memberId) {
  for (const [cid, list] of Object.entries(members)) {
    const m = list.find((x) => x.id === memberId)
    if (m) return { cid: Number(cid), m }
  }
  return null
}

/** PATCH /members/{memberId} */
export function updateMember(memberId, body) {
  const err = storeOnly() ?? checkMember(body)
  if (err) return err
  const found = findMember(memberId)
  if (!found) return fakeError(404, 'MEMBER_NOT_FOUND', '가족 구성원을 찾을 수 없습니다.')
  Object.assign(found.m, body, { nickname: body.nickname.trim() })
  syncWearers(found.cid)
  return fakeResponse(found.m, 250)
}

/** DELETE /members/{memberId}. 과거 판매의 착용자 호칭은 남는다 */
export function deleteMember(memberId) {
  const err = storeOnly()
  if (err) return err
  const found = findMember(memberId)
  if (!found) return fakeError(404, 'MEMBER_NOT_FOUND', '가족 구성원을 찾을 수 없습니다.')
  members[found.cid] = members[found.cid].filter((x) => x.id !== memberId)
  syncWearers(found.cid)
  return fakeResponse(null, 250)
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
    reminderDate: body.reminder?.date ?? null,
    createdAt: new Date().toISOString(),
    updatedAt: null,
    editable: true,
  }
  // 예약(방문 날짜 필수)·재연락(날짜 선택)이면 그날의 할 일을 만든다 (CAL-10·11)
  if (body.type === 'RESERVATION' && !body.reminder?.date) {
    return fakeError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', {
      fieldErrors: [{ field: 'reminderDate', reason: '방문 날짜를 골라 주세요.' }],
    })
  }
  if (body.reminder?.date) {
    const to = body.reminder.staffId ?? author
    const kind = body.type === 'RESERVATION' ? 'VISIT' : 'RECALL'
    followUps[id] = [
      ...(followUps[id] ?? []),
      {
        id: Date.now() + 1,
        kind,
        stage: kind === 'VISIT' ? '방문 예정' : '다시 연락',
        promotional: false,
        saleId: null,
        soldAt: null,
        noteId: note.id,
        noteContent: note.content,
        dueDate: body.reminder.date,
        status: 'OPEN',
        assignee: 'STAFF',
        staffId: to,
        staffName: staff.find((s) => s.id === to)?.name ?? me().name,
        closedAt: null,
      },
    ]
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

/** POST /follow-ups/{id}/visited: 방문 예약 할 일을 '방문함'으로 완료 (CAL-10) */
export function completeVisit(taskId) {
  const { found, error } = closeTask(taskId, 'DONE')
  if (error) return error
  return fakeResponse({ ...found.t }, 250)
}

/** 받는 직원의 할 일 전부 (알림 묶음 계산용, 서버에서는 스케줄러가 읽는다) */
export function tasksOfStaff(staffId) {
  const out = []
  for (const [cid, tasks] of Object.entries(followUps)) {
    const c = customers.find((x) => x.id === Number(cid))
    for (const t of tasks) {
      if (t.staffId === staffId)
        out.push({ ...t, kind: t.kind ?? 'SALE', customerId: c.id, customerName: c.name })
    }
  }
  return out
}

/** POST /follow-ups/{id}/skip */
export function skipFollowUp(taskId) {
  const { found, error } = closeTask(taskId, 'SKIPPED')
  if (error) return error
  return fakeResponse({ ...found.t }, 250)
}

// ---- 오늘 할 일 · 콜리스트 (05 SCR-07) ----

const todayText = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const dayDiff = (a, b) => Math.round((new Date(a) - new Date(b)) / 86400000)

/**
 * GET /follow-ups/today (03 4.7): 만기일이 오늘이거나 지난 OPEN 할 일, 만기일 오래된 순.
 * 직원은 내 할 일, 매장 계정은 매장 전체.
 */
export function getToday() {
  const today = todayText()
  const list = []
  for (const [cid, tasks] of Object.entries(followUps)) {
    const c = customers.find((x) => x.id === Number(cid))
    const d = customerDetails[cid] ?? {}
    for (const t of tasks) {
      if (t.status !== 'OPEN' || t.dueDate > today) continue
      if (isEmployee() && t.staffId !== me().id) continue
      list.push({
        id: t.id,
        customerId: c.id,
        customerName: c.name,
        phoneMasked: c.phoneMasked,
        grade: c.grade,
        stage: t.stage,
        promotional: t.promotional,
        dueDate: t.dueDate,
        overdueDays: Math.max(0, dayDiff(today, t.dueDate)),
        kind: t.kind ?? 'SALE',
        noteContent: t.noteContent ?? null,
        saleId: t.saleId,
        soldAt: t.soldAt,
        marketingAllowed: Boolean(d.marketing?.sms || d.marketing?.kakao),
        assignee: t.assignee,
        staffId: t.staffId,
        staffName: t.staffName,
      })
    }
  }
  list.sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  return fakeResponse(page(list))
}

const gradeOrder = ['NORMAL', 'D', 'C', 'B', 'A']

/**
 * GET /call-list (03 3.3 파라미터). 마지막 방문이 오래된 순.
 * @param {{ scope?: string, staffId?: number, minGrade?: string, inactiveDays?: number,
 *   promoOnly?: boolean, target?: string, itemType?: string, memberRelation?: string }} f
 */
export function getCallList(f = {}) {
  const today = todayText()
  const list = customers
    .map((c) => {
      const d = customerDetails[c.id] ?? {}
      const staffId = d.staff?.id ?? null
      return {
        id: c.id,
        name: c.name,
        phoneMasked: c.phoneMasked,
        grade: c.grade,
        lastVisitAt: c.lastVisitAt,
        createdAt: d.createdAt ?? null,
        staffId,
        staffName: c.staffName,
        marketingAllowed: Boolean(d.marketing?.sms || d.marketing?.kakao),
        gender: d.profile?.gender ?? null,
        preferred: d.profile?.preferredItemTypes ?? [],
        relations: (members[c.id] ?? []).map((m) => m.relation),
      }
    })
    .filter((c) => f.scope !== 'MINE' || c.staffId === me().id)
    .filter((c) => !f.staffId || c.staffId === f.staffId)
    .filter((c) => !f.minGrade || gradeOrder.indexOf(c.grade) >= gradeOrder.indexOf(f.minGrade))
    .filter((c) => {
      if (!f.inactiveDays) return true
      const base = c.lastVisitAt ?? c.createdAt
      return base && dayDiff(today, base.slice(0, 10)) >= f.inactiveDays
    })
    .filter((c) => !f.promoOnly || c.marketingAllowed)
    .filter(
      (c) =>
        !f.target ||
        (f.target === 'WOMEN' ? c.gender === 'F' : f.target === 'MEN' ? c.gender === 'M' : true),
    )
    .filter((c) => !f.itemType || c.preferred.includes(f.itemType))
    .filter((c) => !f.memberRelation || c.relations.includes(f.memberRelation))
    .sort((a, b) =>
      (a.lastVisitAt ?? a.createdAt ?? '').localeCompare(b.lastVisitAt ?? b.createdAt ?? ''),
    )
    .map(
      ({ id, name, phoneMasked, grade, lastVisitAt, createdAt, staffName, marketingAllowed }) => ({
        id,
        name,
        phoneMasked,
        grade,
        lastVisitAt,
        createdAt,
        staffName,
        marketingAllowed,
      }),
    )
  return fakeResponse(page(list))
}

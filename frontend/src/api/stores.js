import { fakeResponse, fakeError } from './fake'
import { stores } from '@/mocks/sales'
import { storeAccounts } from '@/mocks/accounts-store'
import { followUps, customerDetails } from '@/mocks/customerDetail'
import { customers, staff } from '@/mocks/data'

/** 본사 매장·계정 API (05 SCR-08, ADM-01·03, AUTH-04, BR-15). 가짜 데이터 */

const STAFF_ROLES = ['MANAGER', 'SENIOR', 'JUNIOR', 'STAFF']

/** GET /stores: 매장 코드·이름·활성 직원 수 */
export function getStores() {
  return fakeResponse(
    stores.map((s) => ({
      ...s,
      activeStaffCount: (storeAccounts[s.id] ?? []).filter(
        (a) => a.active && STAFF_ROLES.includes(a.role),
      ).length,
    })),
  )
}

export function getStore(id) {
  const s = stores.find((x) => x.id === id)
  return s ? fakeResponse(s) : fakeError(404, 'STORE_NOT_FOUND', '매장을 찾을 수 없습니다.')
}

/** GET /stores/{storeId}/staff (본사는 매장 계정·직원·키오스크 모두) */
export function getStoreAccounts(storeId) {
  return fakeResponse(storeAccounts[storeId] ?? [])
}

/** POST /stores/{storeId}/staff: 직원·키오스크 등록 */
export function createAccount(storeId, body) {
  const list = (storeAccounts[storeId] ??= [])
  if (
    Object.values(storeAccounts)
      .flat()
      .some((a) => a.loginId === body.loginId)
  ) {
    return fakeError(409, 'STAFF_LOGIN_ID_DUPLICATED', '이미 쓰는 로그인 ID입니다.')
  }
  if (body.role === 'MANAGER' && list.some((a) => a.active && a.managerSlot === body.managerSlot)) {
    return fakeError(
      409,
      'MANAGER_SLOT_TAKEN',
      `${body.managerSlot === 'MAIN' ? '메인' : '임시'} 매니저가 이미 있습니다.`,
    )
  }
  const id =
    Math.max(
      ...Object.values(storeAccounts)
        .flat()
        .map((a) => a.id),
    ) + 1
  const acc = {
    id,
    name: body.name,
    role: body.role,
    managerSlot: body.role === 'MANAGER' ? body.managerSlot : null,
    loginId: body.loginId,
    staffCode: body.role === 'KIOSK' ? null : `S${String(id).padStart(3, '0')}`,
    active: true,
  }
  list.push(acc)
  if (storeId === 1 && STAFF_ROLES.includes(acc.role))
    staff.push({ id, name: acc.name, role: acc.role })
  return fakeResponse(acc, 300)
}

const rank = { MANAGER: 4, SENIOR: 3, JUNIOR: 2, STAFF: 1 }

/** 비활성화할 직원의 담당을 넘겨받을 수 있는 사람 (BR-15 초안: 같은 매장 활성 직원 중 직급이 같거나 높은 사람) */
export function handoverCandidates(storeId, accountId) {
  const list = storeAccounts[storeId] ?? []
  const from = list.find((a) => a.id === accountId)
  return list.filter(
    (a) =>
      a.active &&
      a.id !== accountId &&
      STAFF_ROLES.includes(a.role) &&
      rank[a.role] >= rank[from.role],
  )
}

/**
 * POST /staff/{id}/deactivate (03 4.11): 담당 고객·할 일을 넘기고 매장 계정 비밀번호를 바꾼다.
 */
export function deactivateAccount(storeId, accountId, body) {
  const list = storeAccounts[storeId] ?? []
  const acc = list.find((a) => a.id === accountId)
  const to = list.find((a) => a.id === body.handoverTo)
  if (!acc || !to) return fakeError(400, 'HANDOVER_NOT_ALLOWED', '넘겨받을 사람을 골라 주세요.')
  let customerCount = 0
  for (const [cid, d] of Object.entries(customerDetails)) {
    if (d.staff?.id === accountId) {
      d.staff = { id: to.id, name: to.name }
      const c = customers.find((x) => x.id === Number(cid))
      if (c) c.staffName = to.name
      customerCount++
    }
  }
  let taskCount = 0
  for (const tasks of Object.values(followUps)) {
    for (const t of tasks) {
      if (t.status === 'OPEN' && t.staffId === accountId) {
        Object.assign(t, { staffId: to.id, staffName: to.name })
        taskCount++
      }
    }
  }
  acc.active = false
  const i = staff.findIndex((s) => s.id === accountId)
  if (i >= 0) staff.splice(i, 1)
  return fakeResponse({ customerCount, taskCount }, 400)
}

/** 비밀번호 다시 정하기 (초안: 규칙만 확인) */
export function resetPassword(accountId, password) {
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(password)) {
    return fakeError(400, 'PASSWORD_POLICY_VIOLATION', '8자 이상, 영문과 숫자를 섞어 주세요.')
  }
  return fakeResponse(null, 300)
}

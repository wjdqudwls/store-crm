import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '@/stores/session'
import { createMember, deleteMember, getCustomer, getMembers, updateCustomer } from './customers'
import { getCustomerForSale } from './sales'
import { formFromCustomer, toProfilePatch } from '@/features/customer/customerForm'

describe('프로필 수정·가족 편집 (CUS-04·05, 가짜 API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useSessionStore().switchAccount(2) // 강남점 · 매장 계정
  })

  it('should_reject_other_customers_phone_when_profile_updated', async () => {
    const c = await getCustomer(1024, { includeAddress: true })
    const f = formFromCustomer(c, c.addressDetail)
    f.phone = '010-3071-7713' // 윤가람 번호
    await expect(updateCustomer(1024, toProfilePatch(f))).rejects.toMatchObject({
      status: 409,
      body: { details: { customerId: 1502 } },
    })
  })

  it('should_save_staff_and_marketing_when_profile_updated', async () => {
    const c = await getCustomer(1024, { includeAddress: true })
    const f = formFromCustomer(c, c.addressDetail)
    f.staffId = 21
    f.marketingKakao = false
    const res = await updateCustomer(1024, toProfilePatch(f))
    expect(res.staff).toEqual({ id: 21, name: '정민호' })
    expect(res.marketing.kakao).toBe(false)
  })

  it('should_deny_profile_update_when_employee', async () => {
    useSessionStore().switchAccount(17)
    const c = await getCustomer(1024)
    await expect(updateCustomer(1024, toProfilePatch(formFromCustomer(c)))).rejects.toMatchObject({
      status: 403,
    })
  })

  it('should_add_member_and_offer_it_as_wearer_in_sale', async () => {
    const m = await createMember(1187, {
      nickname: '아들',
      relation: 'CHILD',
      gender: 'M',
      sizes: {},
    })
    expect((await getMembers(1187)).map((x) => x.nickname)).toContain('아들')
    expect((await getCustomerForSale(1187)).members).toContainEqual({ id: m.id, label: '아들' })
    await deleteMember(m.id)
    expect((await getMembers(1187)).some((x) => x.id === m.id)).toBe(false)
  })
})

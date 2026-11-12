import { describe, it, expect } from 'vitest'
import { menuFor, allowedPaths, accountLabel } from '@/config/menus'
import { fakeAccounts } from '@/mocks/accounts'

const [store, senior, hq] = fakeAccounts

describe('계정별 메뉴 (05 1.2)', () => {
  it('매장 계정은 PC면 판매 기록, 폰이면 오늘 할 일이 첫 화면이다', () => {
    const m = menuFor('STORE')
    expect(m.firstPc.path).toBe('/sales/new')
    expect(m.firstPhone.path).toBe('/today')
  })

  it('직원 직급은 모두 같은 메뉴다', () => {
    expect(menuFor('MANAGER')).toBe(menuFor('STAFF'))
    expect(menuFor('SENIOR').pc.map((x) => x.label)).toEqual([
      '오늘 할 일',
      '콜리스트',
      '고객',
      '알림함',
    ])
  })

  it('폰 아래 탭은 더보기를 합쳐 4개를 넘지 않는다', () => {
    for (const role of ['STORE', 'SENIOR', 'HQ']) {
      const m = menuFor(role)
      const count = m.phoneTabs.length + (m.phoneMore.length ? 1 : 0)
      expect(count).toBeLessThanOrEqual(4)
    }
  })

  it('판매 기록은 매장 계정만 들어갈 수 있다', () => {
    expect(allowedPaths('STORE').has('/sales/new')).toBe(true)
    expect(allowedPaths('SENIOR').has('/sales/new')).toBe(false)
    expect(allowedPaths('HQ').has('/sales/new')).toBe(false)
  })

  it('위 막대에 계정 이름과 역할을 보여준다 (AUTH-03)', () => {
    expect(accountLabel(store)).toBe('강남점 · 매장 계정')
    expect(accountLabel(senior)).toBe('이지은 · 시니어')
    expect(accountLabel(hq)).toBe('최유나 · 본사')
  })
})

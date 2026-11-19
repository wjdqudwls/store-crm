import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import GradeSummary from './GradeSummary.vue'

vi.mock('@/api/customers', () => ({ getGradeHistory: vi.fn() }))

const grade = (confirmedBasis) => ({
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
  confirmedBasis,
})
const stats = { firstVisitAt: null, lastVisitAt: null, seasonQty: 0, lifetimeAmount: 0 }
const mountWith = (basis) =>
  mount(GradeSummary, {
    global: { plugins: [ElementPlus] },
    props: {
      customer: {
        id: 1,
        grade: grade(basis),
        stats,
        homeStore: { id: 1, name: '강남점' },
        createdAt: null,
      },
    },
  })

describe('GradeSummary 확정 근거 (03 4.4)', () => {
  it('should_안내_문구_when_confirmedBasis가_null인_새_고객', () => {
    const w = mountWith(null)
    expect(w.text()).toContain('아직 확정된 실적 기록이 없습니다')
  })

  it('should_근거_표시_when_confirmedBasis가_있음', () => {
    const w = mountWith({ season: '25/26', ticketCount: 2, netAmount: 2500000, totalScore: 40 })
    expect(w.text()).toContain('25/26 시즌')
    expect(w.text()).toContain('티켓 2건')
  })
})

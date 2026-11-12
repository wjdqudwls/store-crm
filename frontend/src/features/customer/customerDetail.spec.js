import { describe, it, expect } from 'vitest'
import {
  daysAgoLabel,
  manwon,
  marketingAllowed,
  firstTab,
  gradeNotes,
  dateTimeLabel,
} from './customerDetail'

const now = new Date('2026-11-12T10:00:00+09:00')

describe('daysAgoLabel (05 1.4 지난 날)', () => {
  it('should_show_today_when_same_day', () => {
    expect(daysAgoLabel('2026-11-12T08:00:00+09:00', now)).toBe('오늘')
  })
  it('should_count_days_when_past', () => {
    expect(daysAgoLabel('2026-10-15T14:03:00+09:00', now)).toBe('28일 전')
  })
  it('should_show_dash_when_no_visit', () => {
    expect(daysAgoLabel(null, now)).toBe('-')
  })
})

describe('dateTimeLabel (05 1.4 날짜·시각)', () => {
  it('should_use_today_prefix_when_today', () => {
    expect(dateTimeLabel('2026-11-12T09:05:00+09:00', now)).toBe('오늘 09:05')
  })
  it('should_use_short_form_in_list', () => {
    expect(dateTimeLabel('2026-10-15T14:03:00+09:00', now)).toBe('10/15 14:03')
  })
  it('should_use_full_form_in_detail', () => {
    expect(dateTimeLabel('2026-10-15T14:03:00+09:00', now, true)).toBe('2026-10-15 14:03')
  })
})

describe('manwon', () => {
  it('should_round_down_to_10k_won', () => {
    expect(manwon(3420000)).toBe('342만원')
    expect(manwon(5920500)).toBe('592만원')
  })
  it('should_show_won_when_under_10k', () => {
    expect(manwon(0)).toBe('0원')
  })
})

describe('marketingAllowed (BR-08: 문자·카톡 중 하나라도 동의)', () => {
  it('should_be_true_when_either_agreed', () => {
    expect(marketingAllowed({ sms: false, kakao: true })).toBe(true)
  })
  it('should_be_false_when_both_declined', () => {
    expect(marketingAllowed({ sms: false, kakao: false })).toBe(false)
  })
})

describe('firstTab (D4: 들어온 곳에 따라 첫 탭)', () => {
  it('should_open_notes_when_from_today_or_call_list', () => {
    expect(firstTab('today', false)).toBe('notes')
    expect(firstTab('call-list', true)).toBe('notes')
  })
  it('should_open_purchases_when_from_sale', () => {
    expect(firstTab('sale', true)).toBe('purchases')
  })
  it('should_open_summary_on_phone_and_purchases_on_pc_when_from_search', () => {
    expect(firstTab('search', true)).toBe('summary')
    expect(firstTab(undefined, false)).toBe('purchases')
  })
  it('should_fall_back_on_pc_when_summary_asked', () => {
    expect(firstTab('search', false, 'summary')).toBe('purchases')
    expect(firstTab('search', false, 'family')).toBe('family')
  })
})

describe('gradeNotes (GRD-03·05, BR-05)', () => {
  const grade = (patch) => ({
    current: 'B',
    computed: 'B',
    confirmed: 'C',
    confirmedStatus: 'CONFIRMED',
    ...patch,
  })
  it('should_say_higher_applies_always', () => {
    expect(gradeNotes(grade())).toEqual(['둘 중 높은 등급을 적용합니다'])
  })
  it('should_warn_when_computed_lower_than_confirmed', () => {
    expect(gradeNotes(grade({ computed: 'D', current: 'C' }))).toContain(
      '다음 정기 산정(6월 1일)에 실적 기준으로 다시 정해집니다',
    )
  })
  it('should_not_warn_when_normal_vs_normal', () => {
    expect(
      gradeNotes(grade({ computed: 'NORMAL', confirmed: 'NORMAL', current: 'NORMAL' })),
    ).toEqual(['둘 중 높은 등급을 적용합니다'])
  })
})

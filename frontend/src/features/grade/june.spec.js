import { describe, it, expect } from 'vitest'
import { nextJuneFirst } from './june'

describe('다음 6월 1일 (GRD-04)', () => {
  it('6월 1일 전이면 올해 6월 1일', () => {
    expect(nextJuneFirst(new Date('2026-03-10T00:00:00+09:00'))).toBe('2026-06-01')
  })
  it('6월 1일 이후면 내년 6월 1일', () => {
    expect(nextJuneFirst(new Date('2026-11-18T00:00:00+09:00'))).toBe('2027-06-01')
  })
})

import { describe, it, expect } from 'vitest'
import { formatWon, gradeLabel, sizeLabel } from './format'

describe('표시 규칙 (05 1.4)', () => {
  it('금액은 쉼표와 원', () => {
    expect(formatWon(774060)).toBe('774,060원')
    expect(formatWon(0)).toBe('0원')
  })
  it('NORMAL 등급은 일반', () => {
    expect(gradeLabel('NORMAL')).toBe('일반')
    expect(gradeLabel('B')).toBe('B')
  })
  it('여성 아우터만 호수를 붙인다', () => {
    expect(sizeLabel('90', 'OUTER', 'WOMEN')).toBe('90(55)')
    expect(sizeLabel('100', 'OUTER', 'MEN')).toBe('100')
    expect(sizeLabel('M', 'TOP', 'WOMEN')).toBe('M')
  })
})

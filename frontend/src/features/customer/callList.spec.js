import { describe, expect, it } from 'vitest'
import { callListParams } from './callList'

describe('callListParams (03 3.3 콜리스트 기본값 끄기)', () => {
  it('should_끄는_값을_명시_when_조건을_비움(직원이_전체_보기)', () => {
    const p = callListParams({
      scope: 'MINE',
      minGrade: null,
      inactiveDays: null,
      promoOnly: false,
    })
    expect(p).toMatchObject({
      scope: 'MINE',
      minGrade: 'NORMAL',
      inactiveDays: 0,
      promoOnly: false,
    })
  })

  it('should_그대로_보냄_when_직원_기본_조건(D·90일)', () => {
    expect(
      callListParams({ scope: 'MINE', minGrade: 'D', inactiveDays: 90, promoOnly: false }),
    ).toMatchObject({
      minGrade: 'D',
      inactiveDays: 90,
      promoOnly: false,
    })
  })

  it('should_홍보_연락_가능만_보냄_when_매장_계정_기본', () => {
    const p = callListParams({
      scope: 'STORE',
      minGrade: null,
      inactiveDays: null,
      promoOnly: true,
    })
    expect(p).toMatchObject({
      scope: 'STORE',
      promoOnly: true,
      minGrade: 'NORMAL',
      inactiveDays: 0,
    })
  })

  it('should_비운_선택_조건은_보내지_않음_when_null_또는_빈_값', () => {
    const p = callListParams({ staffId: null, target: null, itemType: '', memberRelation: null })
    expect(Object.keys(p).sort()).toEqual(['inactiveDays', 'minGrade', 'promoOnly', 'size'])
  })

  it('should_매장과_선택_조건을_보냄_when_본사가_매장을_고름', () => {
    expect(
      callListParams({
        scope: 'STORE',
        storeId: 2,
        target: 'WOMEN',
        itemType: 'OUTER',
        memberRelation: 'CHILD',
        staffId: 5,
      }),
    ).toMatchObject({
      storeId: 2,
      target: 'WOMEN',
      itemType: 'OUTER',
      memberRelation: 'CHILD',
      staffId: 5,
    })
  })
})

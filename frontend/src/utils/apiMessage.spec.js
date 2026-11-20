import { describe, expect, it } from 'vitest'
import { apiMessage } from './apiMessage'

const FALLBACK = '저장됐는지 확인하지 못했습니다. 다시 눌러 주세요'

describe('apiMessage (05 1.5)', () => {
  it('should_첫_fieldErrors_사유를_붙임_when_400', () => {
    const err = {
      status: 400,
      body: {
        message: '입력값을 확인해 주세요.',
        fieldErrors: [{ field: 'type', reason: '방문 예약 메모는 아직 쓸 수 없습니다.' }],
      },
    }
    expect(apiMessage(err, FALLBACK)).toBe(
      '입력값을 확인해 주세요. (방문 예약 메모는 아직 쓸 수 없습니다.)',
    )
  })

  it('should_서버_message만_when_fieldErrors가_없음', () => {
    expect(
      apiMessage({ body: { message: '메모를 찾을 수 없습니다.', fieldErrors: [] } }, FALLBACK),
    ).toBe('메모를 찾을 수 없습니다.')
  })

  it('should_기본_문장_when_본문이_없음', () => {
    expect(apiMessage(new Error('x'), FALLBACK)).toBe(FALLBACK)
    expect(apiMessage(null, FALLBACK)).toBe(FALLBACK)
  })
})

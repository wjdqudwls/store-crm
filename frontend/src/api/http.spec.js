import { describe, it, expect } from 'vitest'
import { setDevAccountId, toApiError, withDevAccount } from './http'

const res = (status, data) => ({ response: { status, data } })

describe('axios 에러를 화면이 쓰는 모양으로 (05 1.5, 03 1장)', () => {
  it('400은 fieldErrors를 그대로 준다', () => {
    const e = toApiError(
      res(400, {
        code: 'VALIDATION_FAILED',
        message: '입력값 오류',
        fieldErrors: [{ field: 'phone', reason: '형식' }],
      }),
    )
    expect(e).toEqual({
      status: 400,
      body: {
        code: 'VALIDATION_FAILED',
        message: '입력값 오류',
        fieldErrors: [{ field: 'phone', reason: '형식' }],
      },
    })
  })

  it('409의 details를 남긴다 (중복 고객)', () => {
    const e = toApiError(
      res(409, { code: 'CUSTOMER_PHONE_DUPLICATED', message: '중복', details: { customerId: 7 } }),
    )
    expect(e.status).toBe(409)
    expect(e.body.details).toEqual({ customerId: 7 })
    expect(e.body.fieldErrors).toEqual([])
  })

  it('401·403은 상태 코드와 code를 그대로 준다', () => {
    expect(toApiError(res(401, { code: 'AUTH_TOKEN_EXPIRED', message: '만료' })).status).toBe(401)
    expect(toApiError(res(403, { code: 'ACCESS_DENIED', message: '권한' })).body.code).toBe(
      'ACCESS_DENIED',
    )
  })

  it('B0-2의 404·405·500은 traceId가 있어도 같은 모양이다', () => {
    const cases = [
      [404, 'NOT_FOUND', '요청한 주소를 찾을 수 없습니다.'],
      [405, 'METHOD_NOT_ALLOWED', '허용되지 않는 요청 방식입니다.'],
      [500, 'INTERNAL_ERROR', '서버에 문제가 생겼습니다'],
    ]
    for (const [status, code, message] of cases) {
      const e = toApiError(res(status, { code, message, fieldErrors: [], traceId: '8f2c1a' }))
      expect(e).toEqual({ status, body: { code, message, fieldErrors: [] } })
    }
  })

  it('본문이 없는 5xx도 깨지지 않는다', () => {
    const e = toApiError(res(500, undefined))
    expect(e.status).toBe(500)
    expect(e.body.message).toBeTruthy()
  })

  it('응답이 없으면 status 0 (저장됐는지 확인하지 못함)', () => {
    const e = toApiError(new Error('Network Error'))
    expect(e.status).toBe(0)
    expect(e.body.code).toBe('NETWORK_ERROR')
    expect(e.body.message).toContain('확인하지 못했습니다')
  })
})

describe('로그인 전 임시 계정 헤더', () => {
  it('계정을 정하면 X-Dev-Account-Id를 붙이고, 없으면 붙이지 않는다', () => {
    setDevAccountId(null)
    expect(withDevAccount({ headers: {} }).headers).toEqual({})
    setDevAccountId(17)
    expect(withDevAccount({ headers: {} }).headers).toEqual({ 'X-Dev-Account-Id': '17' })
    setDevAccountId(null)
  })
})

import { describe, it, expect, beforeEach, vi } from 'vitest'
import axios from 'axios'
import { http, setAuthLostHandler, toApiError } from './http'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens'

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

/** axios 어댑터를 바꿔 서버 응답을 흉내 낸다. 보낸 요청(주소·헤더)을 기록한다 */
function fakeServer(handler) {
  const sent = []
  http.defaults.adapter = async (config) => {
    sent.push({ url: config.url, auth: config.headers.get('Authorization') })
    const { status, data } = handler(config, sent.length)
    const response = { status, data, config, headers: {}, statusText: '' }
    if (status >= 400) throw Object.assign(new Error('x'), { response, config })
    return response
  }
  return sent
}
const refreshOk = (n = 1) => ({
  accessToken: `access-${n}`,
  refreshToken: `refresh-${n}`,
  tokenType: 'Bearer',
  expiresIn: 1800,
})

describe('로그인 토큰 붙이기와 401 갱신 (F13, 03 3.1)', () => {
  beforeEach(() => {
    clearTokens()
    vi.restoreAllMocks()
    setAuthLostHandler(null)
  })

  it('should_Bearer를_붙임_when_access_토큰이_있음', async () => {
    setTokens({ accessToken: 'abc', refreshToken: 'r' })
    const sent = fakeServer(() => ({ status: 200, data: { ok: true } }))
    await http.get('/customers')
    expect(sent[0].auth).toBe('Bearer abc')
  })

  it('should_붙이지_않음_when_로그인_요청', async () => {
    setTokens({ accessToken: 'abc', refreshToken: 'r' })
    const sent = fakeServer(() => ({ status: 200, data: refreshOk() }))
    await http.post('/auth/login', { loginId: 'a', password: 'b' })
    expect(sent[0].auth).toBeFalsy()
  })

  it('should_refresh_한_번_뒤_같은_요청을_다시_보냄_when_access_만료(401)', async () => {
    setTokens({ accessToken: 'old', refreshToken: 'r1' })
    const sent = fakeServer((config) => {
      if (config.headers.get('Authorization') === 'Bearer old')
        return { status: 401, data: { code: 'AUTH_TOKEN_EXPIRED', message: '만료' } }
      return { status: 200, data: { done: true } }
    })
    vi.spyOn(axios, 'post').mockResolvedValue({ data: refreshOk(2) })
    const res = await http.get('/sales')
    expect(res).toEqual({ done: true })
    expect(axios.post).toHaveBeenCalledTimes(1)
    expect(axios.post.mock.calls[0][1]).toEqual({ refreshToken: 'r1' })
    expect(sent.map((x) => x.auth)).toEqual(['Bearer old', 'Bearer access-2'])
    expect(getAccessToken()).toBe('access-2')
    expect(getRefreshToken()).toBe('refresh-2')
  })

  it('should_refresh_를_한_번만_when_동시에_401_여러_개', async () => {
    setTokens({ accessToken: 'old', refreshToken: 'r1' })
    fakeServer((config) =>
      config.headers.get('Authorization') === 'Bearer old'
        ? { status: 401, data: { code: 'AUTH_TOKEN_INVALID', message: 'x' } }
        : { status: 200, data: { ok: true } },
    )
    vi.spyOn(axios, 'post').mockResolvedValue({ data: refreshOk(2) })
    await Promise.all([http.get('/a'), http.get('/b'), http.get('/c')])
    expect(axios.post).toHaveBeenCalledTimes(1)
  })

  it('should_로그인_화면으로_when_refresh도_실패', async () => {
    setTokens({ accessToken: 'old', refreshToken: 'r1' })
    const lost = vi.fn()
    setAuthLostHandler(lost)
    fakeServer(() => ({ status: 401, data: { code: 'AUTH_TOKEN_EXPIRED', message: '만료' } }))
    vi.spyOn(axios, 'post').mockRejectedValue({ response: { status: 401, data: {} } })
    await expect(http.get('/sales')).rejects.toMatchObject({ status: 401 })
    expect(lost).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('should_갱신_없이_바로_로그인_화면으로_when_비활성_계정(AUTH_ACCOUNT_DISABLED)', async () => {
    setTokens({ accessToken: 'old', refreshToken: 'r1' })
    const lost = vi.fn()
    setAuthLostHandler(lost)
    fakeServer(() => ({ status: 401, data: { code: 'AUTH_ACCOUNT_DISABLED', message: '비활성' } }))
    vi.spyOn(axios, 'post')
    await expect(http.get('/sales')).rejects.toMatchObject({ status: 401 })
    expect(axios.post).not.toHaveBeenCalled()
    expect(lost).toHaveBeenCalledTimes(1)
  })

  it('should_갱신하지_않음_when_로그인_실패(401)와_잠금(429)', async () => {
    vi.spyOn(axios, 'post')
    fakeServer(() => ({ status: 401, data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'x' } }))
    await expect(http.post('/auth/login', {})).rejects.toMatchObject({ status: 401 })
    fakeServer(() => ({ status: 429, data: { code: 'AUTH_LOGIN_LOCKED', message: '잠김' } }))
    await expect(http.post('/auth/login', {})).rejects.toMatchObject({
      status: 429,
      body: { code: 'AUTH_LOGIN_LOCKED' },
    })
    expect(axios.post).not.toHaveBeenCalled()
  })

  it('should_다시_시도하지_않음_when_재시도한_요청이_또_401', async () => {
    setTokens({ accessToken: 'old', refreshToken: 'r1' })
    const sent = fakeServer(() => ({
      status: 401,
      data: { code: 'AUTH_TOKEN_INVALID', message: 'x' },
    }))
    vi.spyOn(axios, 'post').mockResolvedValue({ data: refreshOk(2) })
    await expect(http.get('/sales')).rejects.toMatchObject({ status: 401 })
    expect(sent).toHaveLength(2)
  })
})

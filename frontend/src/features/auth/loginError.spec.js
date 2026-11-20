import { describe, expect, it } from 'vitest'
import { loginErrorMessage } from './loginError'

const err = (status, code, message = '서버 문구') => ({
  status,
  body: { code, message, fieldErrors: [] },
})

describe('loginErrorMessage (03 5장)', () => {
  it('should_잠김_안내_when_429_AUTH_LOGIN_LOCKED', () => {
    expect(loginErrorMessage(err(429, 'AUTH_LOGIN_LOCKED'))).toBe(
      '로그인을 5번 틀려 10분 동안 잠겼습니다. 잠시 뒤 다시 시도해 주세요',
    )
  })

  it('should_같은_문장_when_아이디가_없거나_비밀번호가_틀림(401)', () => {
    expect(loginErrorMessage(err(401, 'AUTH_INVALID_CREDENTIALS'))).toBe(
      '아이디 또는 비밀번호가 맞지 않습니다',
    )
  })

  it('should_비활성_안내_when_AUTH_ACCOUNT_DISABLED', () => {
    expect(loginErrorMessage(err(401, 'AUTH_ACCOUNT_DISABLED'))).toContain('본사에 문의')
  })

  it('should_연결_안내_when_네트워크_끊김', () => {
    expect(
      loginErrorMessage({ status: 0, body: { code: 'NETWORK_ERROR', message: '' } }),
    ).toContain('연결하지 못했습니다')
  })

  it('should_서버_문구_또는_기본_문구_when_그_밖의_오류', () => {
    expect(loginErrorMessage(err(500, 'INTERNAL_ERROR', '일시적인 오류'))).toBe('일시적인 오류')
    expect(loginErrorMessage(null)).toContain('로그인하지 못했습니다')
  })
})

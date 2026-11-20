import { describe, expect, it } from 'vitest'
import { passwordErrors } from './passwordError'

const err = (status, code) => ({ status, body: { code, message: '서버 문구', fieldErrors: [] } })

describe('passwordErrors (03 5장)', () => {
  it('should_현재_비밀번호_칸_when_PASSWORD_MISMATCH', () => {
    expect(Object.keys(passwordErrors(err(400, 'PASSWORD_MISMATCH')))).toEqual(['current'])
  })
  it('should_새_비밀번호_칸_when_PASSWORD_POLICY_VIOLATION', () => {
    expect(Object.keys(passwordErrors(err(400, 'PASSWORD_POLICY_VIOLATION')))).toEqual(['next'])
  })
  it('should_안내_when_403(매장_계정)', () => {
    expect(passwordErrors(err(403, 'ACCESS_DENIED')).current).toContain('바꿀 수 없습니다')
  })
})

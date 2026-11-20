import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens'

/** 테스트 환경(Node 전역 localStorage)과 상관없이 쓰는 간단한 저장소 */
function memoryStorage() {
  const m = new Map()
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => m.set(k, String(v)),
    removeItem: (k) => m.delete(k),
    dump: () => Object.fromEntries(m),
  }
}

describe('토큰 저장 (NFR-04c: 브라우저에는 토큰만)', () => {
  let storage
  beforeEach(() => {
    storage = memoryStorage()
    vi.stubGlobal('localStorage', storage)
    clearTokens()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('should_access는_메모리_refresh는_localStorage에_둠', () => {
    setTokens({ accessToken: 'a', refreshToken: 'r' })
    expect(getAccessToken()).toBe('a')
    expect(getRefreshToken()).toBe('r')
    expect(localStorage.getItem('crm.refresh')).toBe('r')
    // 저장소에는 refresh 하나뿐, access 토큰은 없다
    expect(storage.dump()).toEqual({ 'crm.refresh': 'r' })
  })

  it('should_모두_지움_when_로그아웃', () => {
    setTokens({ accessToken: 'a', refreshToken: 'r' })
    clearTokens()
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(localStorage.getItem('crm.refresh')).toBeNull()
  })

  it('should_깨지지_않음_when_저장소를_못_쓰는_환경', () => {
    storage.setItem = () => {
      throw new Error('blocked')
    }
    expect(() => setTokens({ accessToken: 'a', refreshToken: 'r' })).not.toThrow()
    expect(getAccessToken()).toBe('a')
    expect(getRefreshToken()).toBe('r')
  })
})

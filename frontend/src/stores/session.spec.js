import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = { login: vi.fn(), refresh: vi.fn(), getMe: vi.fn(), logoutAll: vi.fn() }
vi.mock('@/api/auth', () => api)

const tokens = { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresIn: 1800 }
const me = { id: 2, name: '강남점 매장', role: 'STORE', storeId: 1, loginId: 'store-gn01' }

/** 실서버 모드(VITE_API_MODE=real)의 session 스토어 */
async function realStore() {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'real')
  const store = new Map()
  vi.stubGlobal('localStorage', {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
  })
  setActivePinia(createPinia())
  const { useSessionStore } = await import('./session')
  const tk = await import('@/api/tokens')
  return { session: useSessionStore(), tk, store }
}

describe('session 스토어: 실서버 로그인 (F13)', () => {
  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    vi.unstubAllEnvs()
  })

  it('should_토큰_저장하고_me로_계정을_채움_when_로그인', async () => {
    api.login.mockResolvedValue(tokens)
    api.getMe.mockResolvedValue(me)
    const { session, tk, store } = await realStore()
    await session.login('store-gn01', 'pw')
    expect(api.login).toHaveBeenCalledWith('store-gn01', 'pw')
    expect(session.isLoggedIn).toBe(true)
    expect(session.me.role).toBe('STORE')
    expect(tk.getAccessToken()).toBe('a1')
    expect(store.get('crm.refresh')).toBe('r1')
  })

  it('should_오류를_그대로_던지고_로그인_안_됨_when_429_잠금', async () => {
    api.login.mockRejectedValue({
      status: 429,
      body: { code: 'AUTH_LOGIN_LOCKED', message: '잠김' },
    })
    const { session } = await realStore()
    await expect(session.login('x', 'y')).rejects.toMatchObject({ status: 429 })
    expect(session.isLoggedIn).toBe(false)
  })

  it('should_refresh로_되살림_when_새로고침_뒤_restore', async () => {
    api.refresh.mockResolvedValue({ ...tokens, accessToken: 'a2', refreshToken: 'r2' })
    api.getMe.mockResolvedValue(me)
    const { session, tk, store } = await realStore()
    store.set('crm.refresh', 'r1')
    await session.restore()
    expect(api.refresh).toHaveBeenCalledWith('r1')
    expect(session.isLoggedIn).toBe(true)
    expect(tk.getAccessToken()).toBe('a2')
    // 두 번째 호출은 다시 하지 않는다
    await session.restore()
    expect(api.refresh).toHaveBeenCalledTimes(1)
  })

  it('should_로그아웃_상태로_남음_when_refresh가_실패', async () => {
    api.refresh.mockRejectedValue({ status: 401, body: { code: 'AUTH_TOKEN_INVALID' } })
    const { session, store } = await realStore()
    store.set('crm.refresh', 'old')
    await session.restore()
    expect(session.isLoggedIn).toBe(false)
    expect(store.has('crm.refresh')).toBe(false)
  })

  it('should_토큰과_계정을_지움_when_로그아웃', async () => {
    api.login.mockResolvedValue(tokens)
    api.getMe.mockResolvedValue(me)
    const { session, tk, store } = await realStore()
    await session.login('a', 'b')
    session.logout()
    expect(session.isLoggedIn).toBe(false)
    expect(tk.getAccessToken()).toBeNull()
    expect(store.has('crm.refresh')).toBe(false)
  })

  it('should_서버에_알리고_지움_when_모든_기기에서_로그아웃', async () => {
    api.login.mockResolvedValue(tokens)
    api.getMe.mockResolvedValue(me)
    api.logoutAll.mockResolvedValue(undefined)
    const { session } = await realStore()
    await session.login('a', 'b')
    await session.logoutAll()
    expect(api.logoutAll).toHaveBeenCalled()
    expect(session.isLoggedIn).toBe(false)
  })
})

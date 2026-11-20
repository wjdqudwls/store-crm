import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { isFake } from '@/api/mode'
import * as auth from '@/api/auth'
import { clearTokens, getRefreshToken, setTokens } from '@/api/tokens'

// 가짜 계정은 데모·개발(fake)에서만 불러온다. 실서버 빌드에는 들어가지 않는다 (api/mode.js)
const fakeAccounts =
  import.meta.env.VITE_API_MODE !== 'real' ? (await import('@/mocks/accounts')).fakeAccounts : []

/** 실서버에서 로그인 전(또는 로그아웃 뒤)의 빈 계정. 로그인하면 `GET /me` 값으로 바뀐다 */
const anonymous = {
  id: null,
  name: '',
  role: null,
  managerSlot: null,
  storeId: null,
  storeCode: null,
  storeName: null,
  loginId: null,
}

/**
 * 로그인한 계정 (03 3.1, F13).
 * - 실서버: 로그인하면 토큰을 받고 `GET /me`로 계정을 채운다. 새로고침하면 refresh 토큰으로 되살린다 (`restore`).
 * - 가짜(데모): 가짜 계정의 아이디만 맞으면 들어간다 (ADR-032).
 * 계정 정보는 화면 메모리에만 두고, 브라우저 저장소에는 refresh 토큰만 둔다 (NFR-04c, api/tokens.js).
 */
export const useSessionStore = defineStore('session', () => {
  const me = ref(fakeAccounts[0] ?? anonymous)
  const accounts = fakeAccounts
  // 실서버에서 새로고침 뒤 로그인 상태를 한 번 되살렸는지 (라우터가 첫 이동 전에 기다린다)
  let restored = isFake

  const isLoggedIn = computed(() => me.value.id != null)
  const isStore = computed(() => me.value.role === 'STORE')
  const isHq = computed(() => me.value.role === 'HQ')

  function switchAccount(id) {
    const next = accounts.find((a) => a.id === id)
    if (next) me.value = next
  }

  /** 로그인. 실패하면 서버 오류(`{status, body}`)를 그대로 던진다 (401 AUTH_INVALID_CREDENTIALS, 429 AUTH_LOGIN_LOCKED) */
  async function login(loginId, password) {
    if (isFake) {
      await new Promise((r) => setTimeout(r, 300))
      const account = accounts.find((a) => a.loginId === loginId)
      if (!account) {
        return Promise.reject({
          status: 401,
          body: {
            code: 'AUTH_INVALID_CREDENTIALS',
            message: '아이디 또는 비밀번호가 맞지 않습니다',
            fieldErrors: [],
          },
        })
      }
      me.value = account
      return
    }
    setTokens(await auth.login(loginId, password))
    me.value = await auth.getMe()
  }

  /** 실서버: 새로고침 뒤 refresh 토큰이 있으면 로그인 상태를 되살린다. 한 번만 한다 */
  async function restore() {
    if (restored) return
    restored = true
    const refresh = getRefreshToken()
    if (!refresh) return
    try {
      setTokens(await auth.refresh(refresh))
      me.value = await auth.getMe()
    } catch {
      clearTokens()
    }
  }

  /** 로그아웃(이 기기만). 토큰과 화면의 계정 정보를 지운다 */
  function logout() {
    clearTokens()
    if (!isFake) me.value = anonymous
  }

  /** 모든 기기에서 로그아웃: 서버가 이 계정의 모든 토큰을 끊는다 */
  async function logoutAll() {
    if (!isFake) await auth.logoutAll()
    logout()
  }

  return {
    me,
    accounts,
    isLoggedIn,
    isStore,
    isHq,
    switchAccount,
    login,
    restore,
    logout,
    logoutAll,
  }
})

/**
 * 로그인 토큰 보관 (03 3.1, NFR-04c: 브라우저에는 토큰만 둔다).
 * - access(30분)는 메모리에만 둔다. 새로고침하면 refresh로 다시 받는다.
 * - refresh(14일)는 localStorage에 둔다. 매장 노트북·키오스크는 계속 로그인 상태여야 한다 (01 8장, 2026-10-23).
 * 고객 데이터는 어디에도 저장하지 않는다. 로그아웃하면 둘 다 지운다.
 */
const REFRESH_KEY = 'crm.refresh'

let accessToken = null
let refreshMemory = null // 저장소를 못 쓰는 환경(사생활 보호 창 등)에서는 메모리로만 버틴다

export const getAccessToken = () => accessToken

export function getRefreshToken() {
  try {
    return localStorage.getItem(REFRESH_KEY) ?? refreshMemory
  } catch {
    return refreshMemory
  }
}

/** @param {{ accessToken: string, refreshToken: string }} tokens */
export function setTokens({ accessToken: access, refreshToken: refresh }) {
  accessToken = access
  refreshMemory = refresh
  try {
    localStorage.setItem(REFRESH_KEY, refresh)
  } catch {
    // 저장소를 못 쓰면 이 탭에서만 로그인이 유지된다
  }
}

export function clearTokens() {
  accessToken = null
  refreshMemory = null
  try {
    localStorage.removeItem(REFRESH_KEY)
  } catch {
    // 지울 것이 없다
  }
}

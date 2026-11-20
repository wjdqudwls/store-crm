import { http } from './http'

/**
 * 로그인·내 정보 API (03 3.1). 실서버 모드에서만 쓴다. 가짜 모드(데모)의 로그인은 session 스토어가 가짜 계정으로 처리한다.
 * 로그인·갱신 응답: { accessToken, refreshToken, tokenType, expiresIn }
 */
export const login = (loginId, password) => http.post('/auth/login', { loginId, password })

/** 새로고침 뒤 refresh 토큰으로 새 토큰을 받는다 (http.js의 401 갱신과 같은 요청) */
export const refresh = (refreshToken) => http.post('/auth/refresh', { refreshToken })

/** 내 정보: { id, name, role, managerSlot, storeId, storeCode, storeName, loginId } */
export const getMe = () => http.get('/me')

/** 비밀번호 바꾸기(개인 계정만). 성공하면 새 토큰이 온다 (다른 기기의 로그인은 끊긴다) */
export const changePassword = (currentPassword, newPassword) =>
  http.patch('/me/password', { currentPassword, newPassword })

/** 모든 기기에서 로그아웃. 204 */
export const logoutAll = () => http.post('/auth/logout-all')

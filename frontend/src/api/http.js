import axios from 'axios'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens'

/**
 * 실서버 호출 (03 1장). 실패는 가짜 API와 같은 모양 `{ status, body: { code, message, fieldErrors, details } }`로
 * reject해서, 화면의 에러 처리(05 1.5)가 가짜·실제에서 똑같이 돈다.
 */
export const http = axios.create({ baseURL: '/api/v1', timeout: 15000 })

const NETWORK_MESSAGE = '저장됐는지 확인하지 못했습니다. 다시 누르면 같은 요청으로 다시 보냅니다'

/** @param {any} err axios 에러 */
export function toApiError(err) {
  const res = err?.response
  if (!res) {
    return { status: 0, body: { code: 'NETWORK_ERROR', message: NETWORK_MESSAGE, fieldErrors: [] } }
  }
  const d = res.data ?? {}
  return {
    status: res.status,
    body: {
      code: d.code ?? 'UNKNOWN',
      message: d.message ?? '요청을 처리하지 못했습니다.',
      fieldErrors: d.fieldErrors ?? [],
      ...(d.details ? { details: d.details } : {}),
    },
  }
}

const isAuthUrl = (url = '') => /\/auth\/(login|refresh)$/.test(url)

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  // 로그인·갱신 요청에는 토큰을 붙이지 않는다
  if (token && !isAuthUrl(config.url)) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

/** 로그인이 끊겼을 때(갱신 실패·비활성 계정) 할 일. 앱이 로그인 화면으로 보내도록 main.js에서 정한다 */
let authLostHandler = null
export const setAuthLostHandler = (fn) => (authLostHandler = fn)
function authLost() {
  clearTokens()
  authLostHandler?.()
}

/** 동시에 401이 여러 개 와도 갱신은 한 번만 한다. 응답 인터셉터가 없는 axios로 직접 부른다 */
let refreshing = null
function refreshOnce() {
  refreshing ??= (async () => {
    try {
      const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken: getRefreshToken() })
      setTokens(data)
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

// 토큰이 없거나 만료·위조면 같은 401이다(03 5장). 한 번 갱신해서 다시 보내고, 그래도 안 되면 로그인으로 보낸다
const RETRY_CODES = ['AUTH_TOKEN_EXPIRED', 'AUTH_TOKEN_INVALID']

http.interceptors.response.use(
  (res) => res.data, // 성공 응답은 감싸지 않고 리소스 그대로 (03 1장)
  async (err) => {
    const status = err?.response?.status
    const code = err?.response?.data?.code
    const config = err?.config
    if (status === 401 && config && !isAuthUrl(config.url)) {
      if (code === 'AUTH_ACCOUNT_DISABLED') {
        authLost()
      } else if (RETRY_CODES.includes(code) && !config._retried && getRefreshToken()) {
        try {
          await refreshOnce()
          config._retried = true
          return http.request(config)
        } catch (e) {
          // 다시 보낸 요청이 401 말고 다른 오류로 실패하면 그 오류를 그대로 준다
          if (e?.status && e.status !== 401) return Promise.reject(e)
          authLost()
        }
      }
    }
    return Promise.reject(toApiError(err))
  },
)

/** 아직 서버에 연결하지 않은 함수. 연결 PR(12단계)에서 그 파일의 함수부터 채운다 */
export function notConnected(name) {
  return Promise.reject({
    status: 0,
    body: {
      code: 'NOT_CONNECTED',
      message: `${name}은(는) 아직 서버에 연결되지 않았습니다.`,
      fieldErrors: [],
    },
  })
}

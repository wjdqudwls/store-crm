import axios from 'axios'

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

/**
 * 로그인(B0-3) 전 임시 계정 (백엔드 제안, B0-6에서 확정): 헤더 `X-Dev-Account-Id`로 계정을 고른다.
 * 서버는 local 프로필에서만 읽고, B0-3(JWT)에서 지워진다. 데모 계정 바꾸기가 이 값을 정한다.
 */
let devAccountId = null
export const setDevAccountId = (id) => (devAccountId = id)

export function withDevAccount(config) {
  if (devAccountId != null) config.headers['X-Dev-Account-Id'] = String(devAccountId)
  return config
}
http.interceptors.request.use(withDevAccount)

http.interceptors.response.use(
  (res) => res.data, // 성공 응답은 감싸지 않고 리소스 그대로 (03 1장)
  (err) => Promise.reject(toApiError(err)),
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

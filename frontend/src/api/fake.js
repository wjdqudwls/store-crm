/** 가짜 응답을 진짜 API처럼 조금 늦게, 복사본으로 돌려준다 */
export function fakeResponse(data, delayMs = 150) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), delayMs))
}

/** 실패 응답 (03 1장 에러 형식). 409 중복처럼 `details`가 있는 에러도 만든다 */
export function fakeError(
  status,
  code,
  message,
  { details, fieldErrors = [], delayMs = 150 } = {},
) {
  const body = { code, message, fieldErrors, ...(details ? { details } : {}) }
  return new Promise((_, reject) => setTimeout(() => reject({ status, body }), delayMs))
}

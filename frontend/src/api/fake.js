/** 가짜 응답을 진짜 API처럼 조금 늦게, 복사본으로 돌려준다 */
export function fakeResponse(data, delayMs = 150) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), delayMs))
}

/** 실패 응답 (03 1장 에러 형식) */
export function fakeError(status, code, message, delayMs = 150) {
  return new Promise((_, reject) =>
    setTimeout(() => reject({ status, body: { code, message, fieldErrors: [] } }), delayMs),
  )
}

/**
 * 서버 오류를 사용자에게 보일 한 문장으로 (05 1.5).
 * 400의 `fieldErrors` 사유가 있으면 `message` 뒤에 붙인다. 예: "입력값을 확인해 주세요. (방문 예약 메모는 아직 쓸 수 없습니다.)"
 * @param {any} err `{ status, body: { message, fieldErrors } }`
 * @param {string} fallback 본문이 없을 때의 문장
 */
export function apiMessage(err, fallback) {
  const body = err?.body
  const reason = body?.fieldErrors?.[0]?.reason
  const message = body?.message || fallback
  return reason ? `${message} (${reason})` : message
}

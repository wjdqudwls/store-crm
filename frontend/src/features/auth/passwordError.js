/**
 * 비밀번호 바꾸기 실패를 칸별 안내로 (03 5장).
 * 400 PASSWORD_MISMATCH: 현재 비밀번호가 틀림, 400 PASSWORD_POLICY_VIOLATION: 새 비밀번호 규칙 위반.
 * 매장 계정·키오스크는 403이라 화면에서 메뉴가 없지만, 오면 안내만 한다.
 * @returns {Record<string, string>}
 */
export function passwordErrors(err) {
  const code = err?.body?.code
  if (code === 'PASSWORD_MISMATCH') return { current: '현재 비밀번호가 맞지 않습니다' }
  if (code === 'PASSWORD_POLICY_VIOLATION') return { next: '8자 이상, 영문과 숫자를 섞어 주세요' }
  if (err?.status === 403) return { current: '이 계정은 비밀번호를 바꿀 수 없습니다' }
  return { current: err?.body?.message || '바꾸지 못했습니다. 다시 눌러 주세요' }
}

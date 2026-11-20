/**
 * 로그인 실패를 화면 문장으로 (03 5장).
 * 401 AUTH_INVALID_CREDENTIALS: 아이디가 없든 비밀번호가 틀리든 같은 문장(존재 여부를 알려 주지 않는다).
 * 429 AUTH_LOGIN_LOCKED: 같은 아이디를 5번 틀려 10분 잠김.
 */
export function loginErrorMessage(err) {
  const code = err?.body?.code
  if (code === 'AUTH_LOGIN_LOCKED' || err?.status === 429) {
    return '로그인을 5번 틀려 10분 동안 잠겼습니다. 잠시 뒤 다시 시도해 주세요'
  }
  if (code === 'AUTH_INVALID_CREDENTIALS') return '아이디 또는 비밀번호가 맞지 않습니다'
  if (code === 'AUTH_ACCOUNT_DISABLED') return '사용할 수 없는 계정입니다. 본사에 문의해 주세요'
  if (err?.status === 0)
    return err.body?.message || '서버에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요'
  return err?.body?.message || '로그인하지 못했습니다. 잠시 뒤 다시 시도해 주세요'
}

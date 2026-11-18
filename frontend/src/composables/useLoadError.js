import { ref } from 'vue'

/** 불러오기 실패를 화면에 보여 줄 문장으로 (05 1.5). 서버가 준 `message`가 있으면 그것을 쓴다 */
export function loadErrorMessage(err) {
  const msg = err?.body?.message
  if (err?.status === 0) return msg || '서버에 연결하지 못했습니다.'
  return msg || '화면을 불러오지 못했습니다.'
}

/**
 * 화면을 열 때 API가 실패하면 스켈레톤을 멈추고 띠를 보이기 위한 상태.
 * `const { error, run } = useLoadError()` → `run(load)` 로 부르고 `<LoadError :error @retry>` 를 둔다.
 * 실패해도 예외를 밖으로 던지지 않는다 (처리 안 된 에러가 콘솔에 남지 않게).
 * @param {{ ignore?: (err: any) => boolean }} [opts] ignore가 true를 주는 오류(예: 404)는 띠로 보이지 않고 다시 던진다
 */
export function useLoadError(opts = {}) {
  const error = ref(null)
  const retrying = ref(false)

  async function run(fn) {
    error.value = null
    retrying.value = true
    try {
      return await fn()
    } catch (e) {
      if (opts.ignore?.(e)) throw e
      error.value = { message: loadErrorMessage(e), status: e?.status ?? 0, code: e?.body?.code }
      return undefined
    } finally {
      retrying.value = false
    }
  }
  return { error, retrying, run }
}

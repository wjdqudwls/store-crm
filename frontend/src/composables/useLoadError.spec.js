import { describe, expect, it } from 'vitest'
import { loadErrorMessage, useLoadError } from './useLoadError'

const apiError = (status, message, code = 'X') => ({
  status,
  body: { code, message, fieldErrors: [] },
})

describe('useLoadError (F12 공통 에러 띠)', () => {
  it('should_보관_when_불러오기가_실패하면_예외를_던지지_않고', async () => {
    const { error, run } = useLoadError()
    await expect(
      run(() => Promise.reject(apiError(500, '서버 오류', 'INTERNAL_ERROR'))),
    ).resolves.toBeUndefined()
    expect(error.value).toEqual({ message: '서버 오류', status: 500, code: 'INTERNAL_ERROR' })
  })

  it('should_연결_안내_when_네트워크_끊김(status 0)', async () => {
    const { error, run } = useLoadError()
    await run(() =>
      Promise.reject({ status: 0, body: { code: 'NETWORK_ERROR', message: '', fieldErrors: [] } }),
    )
    expect(error.value.status).toBe(0)
    expect(error.value.message).toBeTruthy()
  })

  it('should_기본_문구_when_본문이_없는_오류', () => {
    expect(loadErrorMessage(new Error('boom'))).toBe('화면을 불러오지 못했습니다.')
  })

  it('should_지우고_값_돌려줌_when_다시_시도가_성공', async () => {
    const { error, run } = useLoadError()
    await run(() => Promise.reject(apiError(500, '서버 오류')))
    expect(error.value).not.toBeNull()
    await expect(run(() => Promise.resolve(42))).resolves.toBe(42)
    expect(error.value).toBeNull()
  })

  it('should_띠_없이_다시_던짐_when_ignore가_참(404 등)', async () => {
    const { error, run } = useLoadError({ ignore: (e) => e?.status === 404 })
    await expect(run(() => Promise.reject(apiError(404, '없음')))).rejects.toMatchObject({
      status: 404,
    })
    expect(error.value).toBeNull()
  })
})

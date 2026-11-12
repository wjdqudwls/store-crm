import { fakeResponse } from './fake'
import { sizeOptions } from '@/mocks/data'

/** GET /public/sizes : 품목·대상별 허용 사이즈 (03 4.6 아래) */
export function getSizes() {
  return fakeResponse(sizeOptions)
}

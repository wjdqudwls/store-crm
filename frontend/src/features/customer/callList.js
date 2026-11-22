/**
 * 화면 필터 → `GET /call-list` 쿼리 (03 3.3). 서버는 값을 안 보내면 계정별 기본값을 쓴다
 * (직원: 내 담당·D 이상·90일 이상 미방문, 매장 계정: 홍보 연락 가능만).
 * 화면에서 조건을 비운 것(null)은 "조건 없음"이라서, 기본값이 되살아나지 않게 끄는 값을 명시한다:
 * minGrade=NORMAL · inactiveDays=0 · promoOnly=false.
 * @param {{ scope?: string, staffId?: number|null, storeId?: number|null, minGrade?: string|null,
 *   inactiveDays?: number|null, promoOnly?: boolean|null, target?: string|null,
 *   itemType?: string|null, memberRelation?: string|null }} f
 */
export function callListParams(f = {}) {
  const params = {
    minGrade: f.minGrade ?? 'NORMAL',
    inactiveDays: f.inactiveDays ?? 0,
    promoOnly: f.promoOnly ?? false,
    size: 100,
  }
  for (const key of ['scope', 'staffId', 'storeId', 'target', 'itemType', 'memberRelation']) {
    if (f[key] != null && f[key] !== '') params[key] = f[key]
  }
  return params
}

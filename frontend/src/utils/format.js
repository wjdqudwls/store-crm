/** 표시 규칙 (05 1.4) */

/** 774060 → "774,060원" */
export function formatWon(n) {
  if (n == null) return '-'
  return `${n.toLocaleString('ko-KR')}원`
}

/** 등급 코드 → 화면 글자. NORMAL은 "일반" */
export function gradeLabel(code) {
  return code === 'NORMAL' ? '일반' : code
}

export const itemTypeLabels = {
  OUTER: '아우터',
  TOP: '상의',
  BOTTOM: '하의',
  SHOES: '신발',
  ACC: '액세서리',
}

export const targetLabels = { WOMEN: '여성', MEN: '남성', KIDS: '아동', UNISEX: '공용' }

const womenOuterHo = { 85: '44', 90: '55', 95: '66', 100: '77', 105: '88' }

/** 여성 아우터는 "90(55)"처럼 호수를 붙인다 (05 1.3) */
export function sizeLabel(size, itemType, target) {
  if (itemType === 'OUTER' && target === 'WOMEN' && womenOuterHo[size]) {
    return `${size}(${womenOuterHo[size]})`
  }
  return size
}

/** "2026-11-12" 형식의 오늘 날짜 (한국 시간 브라우저 기준) */
export function todayLabel(d = new Date()) {
  const days = ['일', '월', '화', '수', '목', '금', '토']
  return `${d.getMonth() + 1}/${d.getDate()} (${days[d.getDay()]})`
}

/**
 * 고객 등록 폼의 계산·검증 (05 SCR-04, REG-01·06, 03 4.5).
 * 직원 등록·키오스크·QR이 같은 폼을 쓴다. 서버도 다시 검증한다.
 */

export const sidoList = [
  '서울',
  '부산',
  '대구',
  '인천',
  '광주',
  '대전',
  '울산',
  '세종',
  '경기',
  '강원',
  '충북',
  '충남',
  '전북',
  '전남',
  '경북',
  '경남',
  '제주',
]

/** @returns 빈 폼 */
export function emptyForm() {
  return {
    nationality: 'DOMESTIC',
    name: '',
    nameEn: '',
    phone: '',
    privacy: false,
    age14: false,
    marketingSms: false,
    marketingKakao: false,
    staffId: null,
    birthYear: '',
    birthMonth: null,
    birthDay: null,
    sido: '',
    gu: '',
    detail: '',
    gender: null,
    preferredItemTypes: [],
    sizes: { top: '', outer: '', bottom: '', shoes: '' },
    contactChannel: null,
  }
}

/** 내국인 번호: 숫자만 받아 `010-1234-5678`로 보여준다 */
export function formatDomesticPhone(input) {
  const d = String(input).replace(/\D/g, '').slice(0, 11)
  if (d.length <= 3) return d
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`
  const mid = d.length === 11 ? 7 : 6
  return `${d.slice(0, 3)}-${d.slice(3, mid)}-${d.slice(mid)}`
}

const sizeItemType = { top: 'TOP', outer: 'OUTER', bottom: 'BOTTOM', shoes: 'SHOES' }
const genderTarget = { F: 'WOMEN', M: 'MEN' }

/** 성별을 고르지 않으면 아우터·하의 칸을 숨긴다 (REG-01) */
export function visibleSizeFields(gender) {
  return gender ? ['top', 'outer', 'bottom', 'shoes'] : ['top', 'shoes']
}

/** 고객 프로필의 사이즈 선택지: 성별이 나뉘는 품목은 고객 성별 목록 (02 3.2) */
export function sizeChoices(table, itemType, gender) {
  const rows = table.filter((r) => r.itemType === itemType)
  if (rows.every((r) => r.target === null)) return rows.flatMap((r) => r.sizes)
  const target = genderTarget[gender]
  if (!target) return []
  return rows.filter((r) => r.target === target).flatMap((r) => r.sizes)
}

export { sizeItemType }

const digits = (s) => String(s).replace(/\D/g, '')

/** 외국인 번호는 `+국가번호` 포함. 공백·하이픈은 빼고 보낸다 */
function foreignPhone(s) {
  const t = String(s).replace(/[\s-]/g, '')
  return /^\+\d{8,15}$/.test(t) ? t : null
}

/**
 * 화면 검증. 틀린 칸 → 이유. 비어 있으면 통과.
 * @returns {Record<string, string>}
 */
export function validateRegistration(f) {
  const e = {}
  const foreign = f.nationality === 'FOREIGN'

  if (foreign) {
    if (!f.nameEn.trim()) e.nameEn = '영문 이름을 입력해 주세요'
  } else if (!f.name.trim()) {
    e.name = '이름을 입력해 주세요'
  }

  if (!String(f.phone).trim()) {
    e.phone = '전화번호를 입력해 주세요'
  } else if (foreign) {
    if (!foreignPhone(f.phone)) e.phone = '+국가번호를 포함해 입력해 주세요 (예: +81 90 1234 5678)'
  } else if (!/^01[016789]\d{7,8}$/.test(digits(f.phone))) {
    e.phone = '휴대전화 번호를 확인해 주세요'
  }

  if (!f.privacy) e.privacy = '개인정보 수집·이용에 동의해 주세요'
  if (!f.age14) e.age14 = '만 14세 이상인지 확인해 주세요'

  if (f.birthYear !== '' && f.birthYear != null) {
    const y = Number(f.birthYear)
    const now = new Date().getFullYear()
    if (!/^\d{4}$/.test(String(f.birthYear)) || y < 1900 || y > now) {
      e.birthYear = `1900~${now} 사이 4자리로 입력해 주세요`
    }
  }
  if ((f.birthMonth == null) !== (f.birthDay == null))
    e.birthMd = '생일은 월과 일을 같이 골라 주세요'

  return e
}

const blank = (v) => v == null || String(v).trim() === ''

/** 폼 → `POST /customers` 본문. 빈 선택 항목은 보내지 않는다 */
export function toRegistrationBody(f) {
  const foreign = f.nationality === 'FOREIGN'
  const body = {}
  if (f.staffId != null) body.staffId = f.staffId
  body.nationality = f.nationality
  if (!blank(f.name)) body.name = f.name.trim()
  if (foreign) body.nameEn = f.nameEn.trim()
  body.phone = foreign ? foreignPhone(f.phone) : formatDomesticPhone(f.phone)
  body.agreements = {
    privacy: f.privacy,
    age14: f.age14,
    marketingSms: f.marketingSms,
    marketingKakao: f.marketingKakao,
  }

  if (!blank(f.birthYear)) body.birthYear = Number(f.birthYear)
  if (f.birthMonth != null && f.birthDay != null) {
    body.birthMd = `${String(f.birthMonth).padStart(2, '0')}${String(f.birthDay).padStart(2, '0')}`
  }
  if (!blank(f.sido) || !blank(f.gu) || !blank(f.detail)) {
    body.address = {}
    if (!blank(f.sido)) body.address.sido = f.sido
    if (!blank(f.gu)) body.address.gu = f.gu.trim()
    if (!blank(f.detail)) body.address.detail = f.detail.trim()
  }
  if (f.gender) body.gender = f.gender
  if (f.preferredItemTypes.length) body.preferredItemTypes = [...f.preferredItemTypes]

  const sizes = {}
  for (const key of visibleSizeFields(f.gender)) {
    if (!blank(f.sizes[key])) sizes[key] = f.sizes[key]
  }
  if (Object.keys(sizes).length) body.sizes = sizes
  if (f.contactChannel) body.contactChannel = f.contactChannel
  return body
}

/** 그 달의 날 수 (2월은 29일까지 고를 수 있게) */
export function daysInMonth(month) {
  return month ? new Date(2024, month, 0).getDate() : 31
}

/**
 * 화면용 가짜 데이터. 모양은 03-api.md 4장·4.12 응답과 같다. 이름·번호는 모두 가상이다 (NFR-07).
 * 12단계에서 src/api의 함수만 실제 API(axios)로 바꾼다 (ADR-032).
 */

/** GET /stores/1/staff (활성 직원) */
export const staff = [
  { id: 11, name: '박서연', role: 'MANAGER' },
  { id: 17, name: '이지은', role: 'SENIOR' },
  { id: 21, name: '정민호', role: 'JUNIOR' },
  { id: 24, name: '박도윤', role: 'STAFF' },
  { id: 26, name: '한예린', role: 'STAFF' },
]

/** 등급별 할인율 % (GET /grade-discounts) */
export const gradeDiscounts = { A: 5, B: 3, C: 2, D: 1, NORMAL: 0 }

/** 고객 (검색 목록 + 상세 요약 + 가족 구성원) */
export const customers = [
  {
    id: 1024,
    name: '김하늘',
    phone: '010-2943-4821',
    phoneMasked: '****-4821',
    grade: 'B',
    lastVisitAt: '2026-10-15T14:03:00+09:00',
    staffName: '이지은',
    members: [
      { id: 31, label: '남편' },
      { id: 32, label: '딸' },
    ],
  },
  {
    id: 2310,
    name: '김하늘',
    phone: '010-5512-0937',
    phoneMasked: '****-0937',
    grade: 'NORMAL',
    lastVisitAt: '2025-10-02T11:40:00+09:00',
    staffName: '박도윤',
    members: [],
  },
  {
    id: 1187,
    name: '오세진',
    phone: '010-8820-1290',
    phoneMasked: '****-1290',
    grade: 'A',
    lastVisitAt: '2026-11-08T16:20:00+09:00',
    staffName: '박서연',
    members: [],
  },
  {
    id: 1502,
    name: '윤가람',
    phone: '010-3071-7713',
    phoneMasked: '****-7713',
    grade: 'C',
    lastVisitAt: '2026-09-21T13:10:00+09:00',
    staffName: '정민호',
    members: [{ id: 40, label: '아들' }],
  },
  {
    id: 1733,
    name: '서다인',
    phone: '010-6645-3358',
    phoneMasked: '****-3358',
    grade: 'D',
    lastVisitAt: '2026-06-03T18:45:00+09:00',
    staffName: '한예린',
    members: [],
  },
]

const skus = (start, colors, sizes) =>
  colors.flatMap((color, ci) =>
    sizes.map((size, si) => ({ skuId: start + ci * 10 + si, color, size })),
  )

/** GET /products?keyword= 항목 */
export const products = [
  {
    id: 301,
    productCode: 'D2110',
    name: '울 블렌드 롱코트',
    itemType: 'OUTER',
    target: 'WOMEN',
    listPrice: 798000,
    skus: skus(5501, ['차콜', '카멜'], ['85', '90', '95', '100']),
  },
  {
    id: 302,
    productCode: 'D2115',
    name: '숏 패딩 코트',
    itemType: 'OUTER',
    target: 'WOMEN',
    listPrice: 459000,
    skus: skus(5601, ['블랙', '아이보리'], ['85', '90', '95']),
  },
  {
    id: 310,
    productCode: 'M3020',
    name: '캐시미어 니트',
    itemType: 'TOP',
    target: 'MEN',
    listPrice: 289000,
    skus: skus(5701, ['그레이', '네이비', '오트밀'], ['M', 'L', 'XL']),
  },
  {
    id: 320,
    productCode: 'W4031',
    name: '와이드 슬랙스',
    itemType: 'BOTTOM',
    target: 'WOMEN',
    listPrice: 159000,
    skus: skus(5801, ['블랙', '베이지'], ['24', '26', '28', '30']),
  },
  {
    id: 330,
    productCode: 'A5002',
    name: '캐시미어 머플러',
    itemType: 'ACC',
    target: 'UNISEX',
    listPrice: 129000,
    skus: skus(5901, ['오트밀', '차콜'], ['FREE']),
  },
  {
    id: 340,
    productCode: 'S6010',
    name: '레더 첼시 부츠',
    itemType: 'SHOES',
    target: 'WOMEN',
    listPrice: 339000,
    skus: skus(6001, ['블랙'], ['230', '235', '240', '245', '250']),
  },
]

/** GET /public/sizes (02 3.2 일부) */
export const sizeOptions = [
  { itemType: 'OUTER', target: 'WOMEN', sizes: ['85', '90', '95', '100', '105'] },
  { itemType: 'OUTER', target: 'MEN', sizes: ['95', '100', '105', '110', '115'] },
  { itemType: 'TOP', target: null, sizes: ['XS', 'S', 'M', 'L', 'XL', 'XXL'] },
  { itemType: 'BOTTOM', target: 'WOMEN', sizes: ['24', '26', '28', '30', '32'] },
  { itemType: 'BOTTOM', target: 'MEN', sizes: ['28', '30', '32', '34', '36', '38'] },
  {
    itemType: 'SHOES',
    target: null,
    sizes: [
      '220',
      '225',
      '230',
      '235',
      '240',
      '245',
      '250',
      '255',
      '260',
      '265',
      '270',
      '275',
      '280',
    ],
  },
  { itemType: 'ACC', target: null, sizes: ['FREE'] },
]

/** GET /sales/daily-summary?date=오늘 */
export const dailySummary = { storeId: 1, saleCount: 12, closed: false, closedAt: null }

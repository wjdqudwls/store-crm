/**
 * 계정별 메뉴와 첫 화면 (05-screens.md 1.2).
 * 직원 직급(매니저~스태프)은 모두 같은 화면이다 (R2).
 */

/** 화면 이름. 위 막대(폰)와 메뉴에 같이 쓴다 */
export const screens = {
  saleNew: { path: '/sales/new', label: '판매 기록', task: 'F1' },
  sales: { path: '/sales', label: '판매 내역', task: 'F6' },
  customers: { path: '/customers', label: '고객', task: 'F2' },
  customerNew: { path: '/customers/new', label: '고객 등록', task: 'F4' },
  today: { path: '/today', label: '오늘 할 일', task: 'F3' },
  callList: { path: '/call-list', label: '콜리스트', task: 'F3' },
  notifications: { path: '/notifications', label: '알림함', task: 'F10' },
  products: { path: '/products', label: '상품', task: 'F7' },
  gradeView: { path: '/settings/grade', label: '기준 보기', task: 'F9' },
  settings: { path: '/settings/grade', label: '설정', task: 'F9' },
  stores: { path: '/hq/stores', label: '매장 목록', task: 'F8' },
}

const s = screens

const STORE = {
  pc: [s.saleNew, s.sales, s.customers, s.today, s.callList, s.products, s.gradeView],
  phoneTabs: [s.today, s.callList, s.customers],
  phoneMore: [s.saleNew, s.sales, s.products, s.gradeView],
  extra: [s.customerNew],
  firstPc: s.saleNew,
  firstPhone: s.today,
}

const EMPLOYEE = {
  pc: [s.today, s.callList, s.customers, s.notifications],
  phoneTabs: [s.today, s.callList, s.customers, { ...s.notifications, label: '알림' }],
  phoneMore: [],
  extra: [],
  firstPc: s.today,
  firstPhone: s.today,
}

const HQ = {
  pc: [s.stores, s.customers, s.callList, s.products, s.settings],
  phoneTabs: [s.stores, s.customers, s.callList],
  phoneMore: [s.products, s.settings],
  extra: [],
  firstPc: s.stores,
  firstPhone: s.stores,
}

/** @param {import('../mocks/accounts').Role} role */
export function menuFor(role) {
  if (role === 'STORE') return STORE
  if (role === 'HQ') return HQ
  return EMPLOYEE
}

/** 그 계정이 들어갈 수 있는 화면 경로. 없는 경로로 오면 첫 화면으로 보낸다 (05 1.2) */
export function allowedPaths(role) {
  const m = menuFor(role)
  const paths = [...m.pc, ...m.phoneTabs, ...m.phoneMore, ...m.extra].map((x) => x.path)
  if (m.phoneMore.length) paths.push('/more')
  return new Set(paths)
}

/** 위 막대에 보여줄 계정 이름 (AUTH-03): `강남점 · 매장 계정`, `이지은 · 시니어`, `최유나 · 본사` */
const roleNames = {
  HQ: '본사',
  STORE: '매장 계정',
  MANAGER: '매니저',
  SENIOR: '시니어',
  JUNIOR: '주니어',
  STAFF: '스태프',
  KIOSK: '키오스크',
}

export function accountLabel(me) {
  return `${me.name} · ${roleNames[me.role]}`
}

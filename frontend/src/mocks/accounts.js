/**
 * 가짜 `GET /me` 응답 (03-api.md 3장). 11단계(F13) 로그인 전까지 화면 위의 계정 바꾸기로 고른다 (05 1.2, W13).
 * 이름·매장은 모두 가상 데이터다 (NFR-07).
 *
 * @typedef {'HQ'|'STORE'|'MANAGER'|'SENIOR'|'JUNIOR'|'STAFF'|'KIOSK'} Role
 * @typedef {Object} Me
 * @property {number} id
 * @property {string} name
 * @property {Role} role
 * @property {'MAIN'|'TEMP'|null} managerSlot
 * @property {number|null} storeId
 * @property {string|null} storeCode
 * @property {string|null} storeName
 */

/** @type {Me[]} */
export const fakeAccounts = [
  {
    id: 2,
    name: '강남점',
    role: 'STORE',
    managerSlot: null,
    storeId: 1,
    storeCode: 'GN01',
    storeName: '강남점',
  },
  {
    id: 17,
    name: '이지은',
    role: 'SENIOR',
    managerSlot: null,
    storeId: 1,
    storeCode: 'GN01',
    storeName: '강남점',
  },
  {
    id: 1,
    name: '최유나',
    role: 'HQ',
    managerSlot: null,
    storeId: null,
    storeCode: null,
    storeName: null,
  },
]

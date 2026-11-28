/**
 * 판매(영수증) 가짜 데이터 (03 4.12 영수증 상세 모양). 판매 기록·판매 내역·고객 구매이력이 이 목록 하나를 같이 쓴다.
 * 오늘 판매 12건은 화면을 연 날짜로 만든다. 이름·번호는 모두 가상이다 (NFR-07).
 */
import { customers, products, staff } from './data'

export const stores = [
  { id: 1, code: 'GN01', name: '강남점' },
  { id: 2, code: 'SC02', name: '서초점' },
  { id: 3, code: 'PG03', name: '판교점' },
]

const pad = (n) => String(n).padStart(2, '0')
const ymd = (d) => `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
const iso = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00+09:00`

const staffName = (id) => staff.find((s) => s.id === id)?.name ?? null
const customerName = (id) => customers.find((c) => c.id === id)?.name ?? null

function line(lineId, productCode, productName, itemType, color, size, qty, lineAmount, more = {}) {
  return {
    lineId,
    skuId: null,
    productCode,
    productName,
    itemType,
    color,
    size,
    listPrice: null,
    qty,
    lineAmount,
    returnedQty: 0,
    returnedAmount: 0,
    wearerMemberId: null,
    wearerLabel: null,
    ...more,
  }
}

function receipt(id, storeId, seq, soldAt, sellerStaffId, customerId, grade, lines, more = {}) {
  const store = stores.find((s) => s.id === storeId)
  const d = new Date(soldAt)
  return {
    id,
    receiptNo: `${store.code}-${ymd(d)}-${pad(seq).padStart(4, '0')}`,
    soldAt,
    store: { id: store.id, name: store.name },
    kind: 'NORMAL',
    status: 'COMPLETED',
    customerId,
    customerName: customerName(customerId),
    sellerStaffId,
    sellerName: staffName(sellerStaffId),
    appliedGrade: grade,
    lines,
    returns: [],
    originSaleId: null,
    originReceiptNo: null,
    totalQty: lines.reduce((s, l) => s + l.qty, 0),
    totalAmount: lines.reduce((s, l) => s + l.lineAmount, 0),
    ...more,
  }
}

/** 지난 판매 (고객 상세 구매이력에 나오는 것) */
const past = [
  receipt(
    88231,
    1,
    12,
    '2026-10-15T14:03:00+09:00',
    17,
    1024,
    'C',
    [
      line(1, 'D2110', '울 블렌드 롱코트', 'OUTER', '차콜', '90', 1, 774060, { listPrice: 798000 }),
      line(2, null, '캐시미어 머플러', 'ACC', '오트밀', 'FREE', 1, 217800, {
        returnedQty: 1,
        returnedAmount: 217800,
        wearerMemberId: 31,
        wearerLabel: '남편',
      }),
    ],
    {
      status: 'PARTIAL_RETURNED',
      returns: [
        {
          returnId: 5021,
          kind: 'RETURN',
          returnedAt: '2026-10-16T11:20:00+09:00',
          staffName: '이지은',
          amount: 217800,
          newSaleId: null,
          newReceiptNo: null,
        },
      ],
    },
  ),
  receipt(86120, 1, 4, '2026-07-12T16:40:00+09:00', 11, 1024, 'C', [
    line(1, 'D3305', '린넨 셔츠 원피스', 'TOP', '아이보리', 'M', 1, 279000),
    line(2, 'K1102', '아동 데님 점퍼', 'OUTER', '인디고', '8', 1, 149000, {
      wearerMemberId: 32,
      wearerLabel: '딸',
    }),
    line(3, 'D2204', '라이트 트렌치코트', 'OUTER', '베이지', '90', 1, 1000000),
  ]),
  receipt(84077, 2, 21, '2026-04-05T12:15:00+09:00', 11, 1024, 'NORMAL', [
    line(1, 'D4120', '울 와이드 팬츠', 'BOTTOM', '블랙', '26', 2, 556140),
    line(2, 'S0710', '레더 로퍼', 'SHOES', '브라운', '240', 1, 444000),
  ]),
  receipt(88402, 1, 9, '2026-11-08T16:20:00+09:00', 17, 1187, 'A', [
    line(1, 'M2010', '캐시미어 더블 코트', 'OUTER', '네이비', '105', 1, 1515250),
  ]),
  receipt(87700, 1, 3, '2026-09-21T13:10:00+09:00', 21, 1502, 'C', [
    line(1, 'D5501', '실크 블라우스', 'TOP', '블랙', 'S', 2, 820000),
  ]),
  receipt(80112, 1, 7, '2025-10-02T11:40:00+09:00', 24, 2310, 'NORMAL', [
    line(1, 'W4031', '와이드 슬랙스', 'BOTTOM', '블랙', '26', 1, 159000),
  ]),
]

/** 오늘 판매 12건 (판매 사원, 고객(null = 비회원), 상품 순번, 시:분) */
const todayPlan = [
  [17, null, 2, '10:42'],
  [21, null, 3, '11:05'],
  [11, null, 0, '11:38'],
  [24, null, 4, '12:10'],
  [26, null, 1, '12:55'],
  [17, null, 5, '13:20'],
  [21, null, 2, '13:48'],
  [11, null, 3, '14:15'],
  [24, null, 4, '14:40'],
  [26, null, 0, '15:05'],
  [17, null, 3, '15:30'],
  [21, null, 2, '15:52'],
]

function todaySales() {
  const now = new Date()
  return todayPlan.map(([seller, customerId, pi, hm], i) => {
    const [h, m] = hm.split(':').map(Number)
    const d = new Date(now)
    d.setHours(h, m, 0, 0)
    const p = products[pi]
    const sku = p.skus[1] ?? p.skus[0]
    const rate = customerId ? 1 : 0
    const amount = Math.floor((p.listPrice * (100 - rate)) / 100)
    return receipt(89001 + i, 1, i + 1, iso(d), seller, customerId, customerId ? 'D' : null, [
      {
        ...line(1, p.productCode, p.name, p.itemType, sku.color, sku.size, 1, amount),
        skuId: sku.skuId,
        listPrice: p.listPrice,
      },
    ])
  })
}

/** 모든 판매. 새 판매는 앞에 넣는다 (최신순) */
export const sales = [...todaySales(), ...past].sort((a, b) => b.soldAt.localeCompare(a.soldAt))

/** 하루 마감 기록 (매장별, 날짜별). 지난 날 마감 몇 개 */
export const dayCloses = [
  {
    storeId: 1,
    date: '2026-11-11',
    closedAt: '2026-11-11T21:05:00+09:00',
    closedByName: '강남점',
    reopenedAt: null,
    reopenedByName: null,
  },
  {
    storeId: 1,
    date: '2026-11-10',
    closedAt: '2026-11-10T21:12:00+09:00',
    closedByName: '강남점',
    reopenedAt: '2026-11-10T21:40:00+09:00',
    reopenedByName: '최유나',
  },
  {
    storeId: 1,
    date: '2026-11-09',
    closedAt: '2026-11-09T20:58:00+09:00',
    closedByName: '강남점',
    reopenedAt: null,
    reopenedByName: null,
  },
]

export function todayKey(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export { iso as toIso, ymd as toYmd }

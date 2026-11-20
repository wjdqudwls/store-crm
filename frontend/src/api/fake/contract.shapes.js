/**
 * 03-api.md의 응답 모양을 필드 목록으로 고정한 것 (4.1~4.9, 4.12). 가짜 응답과 자동 비교하고(contract.spec.js),
 * 백엔드가 만든 실제 응답에도 같은 기대값을 쓴다 (12단계).
 *
 * 모양 표기: 'number' | 'string' | 'boolean' | 'any'. 값이 null일 수 있으면 `'string|null'`,
 * 없을 수 있는 칸이면 칸 이름 앞에 `?`. 배열은 `[모양]`, 객체는 그대로 중첩한다.
 */

const sale = {
  id: 'number',
  receiptNo: 'string',
  soldAt: 'string',
  kind: 'string',
  sellerStaffId: 'number',
  customerId: 'number|null',
  appliedGrade: 'string|null',
  lines: [
    {
      lineId: 'number',
      skuId: 'number|null',
      productCode: 'string|null',
      productName: 'string',
      itemType: 'string',
      color: 'string|null',
      size: 'string',
      listPrice: 'number|null',
      qty: 'number',
      lineAmount: 'number',
      '?wearerMemberId': 'number|null',
      '?wearerLabel': 'string|null',
    },
  ],
  totalAmount: 'number',
  totalQty: 'number',
}

const page = (item) => ({
  content: [item],
  page: 'number',
  size: 'number',
  totalElements: 'number',
  totalPages: 'number',
})

export const shapes = {
  /** 4.12 오늘 판매 요약 */
  dailySummary: [
    {
      storeId: 'number',
      date: 'string',
      saleCount: 'number',
      closed: 'boolean',
      closedAt: 'string|null',
    },
  ],

  /** 4.1 판매등록 응답 */
  saleCreated: sale,

  /** 4.12 판매 내역 항목 */
  saleListItem: {
    id: 'number',
    receiptNo: 'string',
    soldAt: 'string',
    kind: 'string',
    status: 'string',
    customerId: 'number|null',
    customerName: 'string|null',
    sellerStaffId: 'number',
    sellerName: 'string',
    totalQty: 'number',
    totalAmount: 'number',
    originSaleId: 'number|null',
    originReceiptNo: 'string|null',
  },

  /** 4.12 영수증 상세 */
  saleDetail: {
    id: 'number',
    receiptNo: 'string',
    soldAt: 'string',
    store: { id: 'number', name: 'string' },
    kind: 'string',
    status: 'string',
    customerId: 'number|null',
    customerName: 'string|null',
    sellerStaffId: 'number',
    sellerName: 'string',
    appliedGrade: 'string|null',
    lines: [
      {
        lineId: 'number',
        skuId: 'number|null',
        productCode: 'string|null',
        productName: 'string',
        itemType: 'string',
        color: 'string|null',
        size: 'string',
        listPrice: 'number|null',
        qty: 'number',
        lineAmount: 'number',
        returnedQty: 'number',
        returnedAmount: 'number',
        wearerLabel: 'string|null',
      },
    ],
    returns: [
      {
        returnId: 'number',
        kind: 'string',
        returnedAt: 'string',
        staffName: 'string',
        amount: 'number',
        newSaleId: 'number|null',
        newReceiptNo: 'string|null',
      },
    ],
    originSaleId: 'number|null',
    originReceiptNo: 'string|null',
    totalQty: 'number',
    totalAmount: 'number',
  },

  /** 4.2 반품 응답 (03에 칸 이름이 글로만 있다: 반품 ID, 반품 금액, 새 상태) */
  returnCreated: { returnId: 'number', returnedAmount: 'number', status: 'string' },

  /** 4.8 교환 응답 */
  exchangeCreated: {
    returnId: 'number',
    returnedAmount: 'number',
    newSale: sale,
    extraPaid: 'number',
  },

  /** 4.12 고객 구매이력 항목 (+ storeId, 문서 세션이 추가) */
  purchaseItem: {
    saleId: 'number',
    receiptNo: 'string',
    soldAt: 'string',
    storeId: 'number',
    storeName: 'string',
    kind: 'string',
    status: 'string',
    totalAmount: 'number',
    lines: [
      {
        lineId: 'number',
        productCode: 'string|null',
        productName: 'string',
        color: 'string|null',
        size: 'string',
        qty: 'number',
        lineAmount: 'number',
        returnedQty: 'number',
        returnedAmount: 'number',
        wearerLabel: 'string|null',
      },
    ],
  },

  /** 4.12 상품 검색 항목 */
  productItem: {
    id: 'number',
    productCode: 'string',
    name: 'string',
    itemType: 'string',
    target: 'string|null',
    listPrice: 'number',
    skus: [{ skuId: 'number', color: 'string|null', size: 'string' }],
  },

  /** 4.12 사이즈 목록 */
  sizes: [{ itemType: 'string', target: 'string|null', sizes: ['string'] }],

  /** 4.12 QR 첫 정보 */
  joinInfo: {
    store: { code: 'string', name: 'string' },
    consents: { privacy: 'string', age14: 'string', marketing: 'string' },
    sizes: [{ itemType: 'string', target: 'string|null', sizes: ['string'] }],
  },

  /** 4.3 고객 검색 항목 */
  customerSearchItem: {
    id: 'number',
    name: 'string',
    phoneMasked: 'string',
    grade: 'string',
    lastVisitAt: 'string|null',
    staffName: 'string|null',
  },

  /** 4.4 고객 상세 */
  customerDetail: {
    id: 'number',
    name: 'string',
    nameEn: 'string|null',
    nationality: 'string',
    phone: 'string',
    createdAt: 'string',
    homeStore: { id: 'number', name: 'string' },
    staff: { id: 'number', name: 'string' },
    grade: {
      current: 'string',
      computed: 'string',
      confirmed: 'string',
      confirmedStatus: 'string',
      season: 'string',
      seasonTicketCount: 'number',
      ticketScore: 'number',
      seasonNetAmount: 'number',
      amountScore: 'number',
      totalScore: 'number',
      discountRate: 'number',
      policyVersion: 'number',
      confirmedBasis: {
        season: 'string',
        ticketCount: 'number',
        netAmount: 'number',
        totalScore: 'number',
      },
    },
    stats: {
      firstVisitAt: 'string|null',
      lastVisitAt: 'string|null',
      seasonQty: 'number',
      lifetimeAmount: 'number',
    },
    profile: {
      gender: 'string|null',
      birthYear: 'number|null',
      birthMd: 'string|null',
      regionSido: 'string|null',
      regionGu: 'string|null',
      preferredItemTypes: ['string'],
      sizes: {
        top: 'string|null',
        outer: 'string|null',
        bottom: 'string|null',
        shoes: 'string|null',
      },
      contactChannel: 'string|null',
    },
    marketing: { sms: 'boolean', kakao: 'boolean' },
  },

  /** 4.7 오늘 할 일 항목 */
  todayItem: {
    id: 'number',
    customerId: 'number',
    customerName: 'string',
    phoneMasked: 'string',
    grade: 'string',
    stage: 'string',
    promotional: 'boolean',
    dueDate: 'string',
    overdueDays: 'number',
    saleId: 'number|null',
    soldAt: 'string|null',
    noteContent: 'string|null',
    kind: 'string',
    marketingAllowed: 'boolean',
    assignee: 'string',
    staffId: 'number',
    staffName: 'string',
  },

  /** 4.12 고객 할 일 항목 */
  followUpItem: {
    id: 'number',
    kind: 'string',
    stage: 'string',
    promotional: 'boolean',
    saleId: 'number|null',
    soldAt: 'string|null',
    noteContent: 'string|null',
    dueDate: 'string',
    status: 'string',
    assignee: 'string',
    staffId: 'number',
    staffName: 'string',
    closedAt: 'string|null',
  },

  /** 4.12 콜리스트 항목 */
  callListItem: {
    id: 'number',
    name: 'string',
    phoneMasked: 'string',
    grade: 'string',
    lastVisitAt: 'string|null',
    createdAt: 'string',
    staffName: 'string|null',
    marketingAllowed: 'boolean',
  },

  /** 4.12 메모 항목 */
  noteItem: {
    id: 'number',
    type: 'string',
    content: 'string',
    callResult: 'string|null',
    channel: 'string|null',
    staffId: 'number',
    staffName: 'string',
    saleId: 'number|null',
    followUpId: 'number|null',
    reminderDate: 'string|null',
    createdAt: 'string',
    updatedAt: 'string|null',
    editable: 'boolean',
  },

  /** 4.12 알림함 */
  notifications: {
    unreadCount: 'number',
    content: [
      {
        id: 'number',
        type: 'string',
        customerId: 'number|null',
        refId: 'number|null',
        message: 'string',
        createdAt: 'string',
        read: 'boolean',
      },
    ],
    page: 'number',
    size: 'number',
    totalElements: 'number',
    totalPages: 'number',
  },

  /** 4.12 계정 목록 항목 (본사가 받는 전체 모양) */
  staffItem: {
    id: 'number',
    name: 'string',
    role: 'string',
    managerSlot: 'string|null',
    loginId: 'string',
    staffCode: 'string|null',
    active: 'boolean',
  },

  /** 4.12 마감 현황 항목 */
  dayCloseItem: {
    date: 'string',
    closedAt: 'string',
    closedByName: 'string',
    reopenedAt: 'string|null',
    reopenedByName: 'string|null',
  },

  /** 4.12 등급 기준 */
  gradePolicy: {
    version: 'number',
    appliesFrom: 'string',
    periodMonths: 'number',
    amountRules: [{ minValue: 'number', score: 'number' }],
    ticketRules: [{ minValue: 'number', score: 'number' }],
    gradeCuts: [{ grade: 'string', minScore: 'number' }],
    discounts: [{ grade: 'string', rate: 'number' }],
    next: { version: 'number', appliesFrom: 'string' },
  },

  /** 4.12 등급 이력 항목 */
  gradeHistoryItem: {
    id: 'number',
    fromGrade: 'string',
    toGrade: 'string',
    reason: 'string',
    season: 'string',
    ticketCount: 'number',
    netAmount: 'number',
    totalScore: 'number',
    policyVersion: 'number',
    createdAt: 'string',
  },

  /** 4.12 정기 산정 실행 결과 */
  periodicRun: {
    season: 'string',
    nextSeason: 'string',
    policyVersion: 'number',
    customerCount: 'number',
    changedCount: 'number',
    historyCreated: 'number',
    elapsedMs: 'number',
  },

  /** 목록 응답 틀 (1장) */
  page,
}

const typeOf = (v) => (v === null ? 'null' : Array.isArray(v) ? 'array' : typeof v)

/**
 * 값이 모양과 같은지 비교해 차이 목록을 만든다.
 * 종류: `missing`(03에 있는데 응답에 없음), `extra`(응답에만 있음), `type`(값 종류가 다름)
 * @returns {{ kind: 'missing'|'extra'|'type', path: string, detail?: string }[]}
 */
export function compareShape(value, shape, path = '') {
  const diffs = []
  if (Array.isArray(shape)) {
    if (!Array.isArray(value))
      return [{ kind: 'type', path, detail: `배열이어야 하는데 ${typeOf(value)}` }]
    if (!value.length) return diffs // 빈 배열은 비교할 값이 없다
    return compareShape(value[0], shape[0], `${path}[]`)
  }
  if (typeof shape === 'string') {
    const allowed = shape.split('|')
    const t = typeOf(value)
    if (!allowed.includes(t) && !allowed.includes('any')) {
      diffs.push({ kind: 'type', path, detail: `${shape}이어야 하는데 ${t}` })
    }
    return diffs
  }
  if (typeOf(value) !== 'object') {
    return [{ kind: 'type', path, detail: `객체이어야 하는데 ${typeOf(value)}` }]
  }
  const keys = new Set()
  for (const [rawKey, sub] of Object.entries(shape)) {
    const optional = rawKey.startsWith('?')
    const key = optional ? rawKey.slice(1) : rawKey
    keys.add(key)
    const p = path ? `${path}.${key}` : key
    if (!(key in value)) {
      if (!optional) diffs.push({ kind: 'missing', path: p })
      continue
    }
    // `|null`로 선언된 칸의 중첩 객체는 null이면 건너뛴다
    if (value[key] === null && (typeof sub !== 'string' || sub.includes('null'))) continue
    diffs.push(...compareShape(value[key], sub, p))
  }
  for (const key of Object.keys(value)) {
    if (!keys.has(key)) diffs.push({ kind: 'extra', path: path ? `${path}.${key}` : key })
  }
  return diffs
}

import { describe, it, expect, beforeAll } from 'vitest'
import { collect } from './contract.collect'
import { compareShape, shapes } from './contract.shapes'

/**
 * 가짜 응답이 03-api.md의 응답 모양과 같은지 자동으로 비교한다 (12단계에서 실제 응답에도 같은 기준을 쓴다).
 * 알려진 차이는 이유와 함께 아래에 적는다. 새 차이가 생기거나 차이가 사라지면 이 테스트가 깨져서 목록을 고치게 한다.
 */
const KNOWN = {
  joinInfo: {
    reason:
      '참고 화면(키오스크·QR) · 03은 서버가 동의 문구(consents)를 주지만 화면이 직접 갖고 있다',
    diffs: ['missing:consents'],
  },
  gradePolicy: {
    reason: "참고 화면(설정) · '지난 버전' 목록(versions)은 03에 넣지 않기로 했다 (총괄 결정)",
    diffs: ['extra:versions'],
  },
  followUpItem: {
    reason: '가짜 할 일에 noteId(연결된 메모)가 있다. 03 항목에는 없고 화면은 noteContent만 쓴다',
    diffs: ['extra:noteId'],
  },
  noteItem: {
    reason:
      '03 메모 항목에 reminderDate: string|null이 추가된다 (문서 세션). 반영되면 shapes에 넣고 이 항목을 지운다',
    diffs: ['extra:reminderDate'],
  },
  saleCreated: {
    reason: '가짜는 영수증 상세 전체를 준다(상위집합). 화면은 id·receiptNo만 쓴다',
    diffs: [
      'extra:lines[].returnedQty',
      'extra:lines[].returnedAmount',
      'extra:store',
      'extra:status',
      'extra:customerName',
      'extra:sellerName',
      'extra:returns',
      'extra:originSaleId',
      'extra:originReceiptNo',
    ],
  },
  exchangeCreated: {
    reason:
      '교환의 새 판매도 같은 상위집합. 03 4.8은 "4.1 응답과 같은 모양"이라 originSaleId는 모호',
    diffs: [
      'extra:newSale.lines[].returnedQty',
      'extra:newSale.lines[].returnedAmount',
      'extra:newSale.store',
      'extra:newSale.status',
      'extra:newSale.customerName',
      'extra:newSale.sellerName',
      'extra:newSale.returns',
      'extra:newSale.originSaleId',
      'extra:newSale.originReceiptNo',
    ],
  },
}

describe('가짜 응답 ↔ 03 응답 모양', () => {
  let data
  beforeAll(async () => {
    data = await collect()
  }, 30000)

  it('should_have_a_shape_for_every_collected_response', () => {
    expect(Object.keys(data).filter((k) => !shapes[k])).toEqual([])
  })

  for (const name of Object.keys(shapes).filter((k) => k !== 'page')) {
    it(`${name}: 03과 같거나 알려진 차이만 있다`, () => {
      const diffs = compareShape(data[name], shapes[name]).map((d) => `${d.kind}:${d.path}`)
      expect(diffs.sort()).toEqual([...(KNOWN[name]?.diffs ?? [])].sort())
    })
  }
})

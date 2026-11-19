import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '@/stores/session'
import { correctSale, exchangeSale, getSale, returnSale, voidSale } from './sales'

const directLine = (lineAmount) => ({
  productName: '테스트 코트',
  itemType: 'OUTER',
  size: '105',
  qty: 1,
  lineAmount,
})

describe('반품·교환·정정 (SAL-03·06·07, 가짜 API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useSessionStore().switchAccount(2) // 강남점 · 매장 계정
  })

  it('should_return_past_sale_once_when_same_key_sent_twice', async () => {
    const body = { staffId: 17, lines: [{ lineId: 1, qty: 1 }] }
    const first = await returnSale(86120, body, 'ret-key-1')
    const again = await returnSale(86120, body, 'ret-key-1')
    expect(first).toEqual(again)
    expect(first).toMatchObject({ returnedAmount: 279000, status: 'PARTIAL_RETURNED' })
    const sale = await getSale(86120)
    expect(sale.lines[0].returnedQty).toBe(1)
    expect(sale.returns).toHaveLength(1)
  })

  it('should_reject_return_when_qty_exceeds_left', async () => {
    const body = { staffId: 17, lines: [{ lineId: 1, qty: 1 }] }
    await expect(returnSale(86120, body, 'ret-key-2')).rejects.toMatchObject({
      status: 409,
      body: { code: 'RETURN_QTY_EXCEEDED' },
    })
  })

  it('should_create_new_receipt_linked_to_origin_when_exchanged', async () => {
    const res = await exchangeSale(
      88402,
      { staffId: 21, returnLines: [{ lineId: 1, qty: 1 }], newLines: [directLine(1600000)] },
      'exc-key-1',
    )
    expect(res.returnedAmount).toBe(1515250)
    expect(res.extraPaid).toBe(84750)
    expect(res.newSale).toMatchObject({
      kind: 'EXCHANGE',
      originSaleId: 88402,
      sellerStaffId: 21,
      customerId: 1187,
    })
    const origin = await getSale(88402)
    expect(origin.status).toBe('RETURNED')
    expect(origin.returns.at(-1)).toMatchObject({
      kind: 'EXCHANGE',
      newSaleId: res.newSale.id,
      newReceiptNo: res.newSale.receiptNo,
    })
  })

  it('should_keep_seller_and_time_and_void_origin_when_corrected', async () => {
    const origin = await getSale(89001)
    const res = await correctSale(
      89001,
      { staffId: 11, customerId: 1024, lines: [directLine(500000)] },
      'cor-key-1',
    )
    expect(res).toMatchObject({
      kind: 'CORRECTION',
      originSaleId: 89001,
      soldAt: origin.soldAt,
      sellerStaffId: origin.sellerStaffId,
      customerId: 1024,
      totalAmount: 500000,
    })
    const voided = await getSale(89001)
    expect(voided.status).toBe('VOIDED')
    expect(voided.returns.at(-1)).toMatchObject({ kind: 'VOID', newSaleId: res.id })
  })

  it('should_deny_return_exchange_void_when_sale_is_of_another_store', async () => {
    // 84077은 서초점 영수증. 강남점 매장 계정은 보기만 한다
    const ret = { staffId: 17, lines: [{ lineId: 1, qty: 1 }] }
    await expect(returnSale(84077, ret, 'other-1')).rejects.toMatchObject({ status: 403 })
    await expect(
      exchangeSale(
        84077,
        { staffId: 17, returnLines: ret.lines, newLines: [directLine(1000)] },
        'other-2',
      ),
    ).rejects.toMatchObject({ status: 403 })
    await expect(voidSale(84077, { staffId: 17 })).rejects.toMatchObject({ status: 403 })
    expect((await getSale(84077)).lines[0].returnedQty).toBe(0)
  })

  it('should_lock_past_sale_correction_when_store_but_allow_when_hq', async () => {
    const body = { customerId: 1502, lines: [directLine(800000)] }
    await expect(correctSale(87700, body, 'cor-key-2')).rejects.toMatchObject({
      status: 409,
      body: { code: 'SALE_LOCKED' },
    })
    useSessionStore().switchAccount(1) // 본사
    const res = await correctSale(87700, body, 'cor-key-3')
    expect(res.receiptNo).toMatch(/^GN01-20260921-/)
  })
})

describe('가짜 API: 마감·기록 취소 요청 키 (03 4.10, BR-12)', () => {
  it('should_200_같은_항목_when_이미_마감한_날_다시_마감', async () => {
    const { closeDay } = await import('./sales')
    const first = await closeDay()
    const second = await closeDay()
    expect(second.closedAt).toBe(first.closedAt)
  })
})

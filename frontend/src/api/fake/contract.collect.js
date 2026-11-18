import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '@/stores/session'
import * as sales from './sales'
import * as customers from './customers'
import * as products from './products'
import * as pub from './public'
import * as notifications from './notifications'
import * as settings from './settings'
import * as stores from './stores'

/** 가짜 API를 불러 03 모양과 비교할 응답을 모은다. 매장 계정(강남점)으로 */
export async function collect() {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.switchAccount(2)
  const out = {}
  out.dailySummary = await sales.getDailySummary()
  out.saleListItem = (await sales.getSales()).content[0]
  out.saleDetail = await sales.getSale(88231)
  out.purchaseItem = (await customers.getPurchases(1024)).content[0]
  out.productItem = (await products.getProducts('코트')).content[0]
  out.sizes = await pub.getSizes()
  out.joinInfo = await pub.getJoinInfo('GN01')
  out.customerSearchItem = (await customers.searchCustomers('김')).content[0]
  out.customerDetail = await customers.getCustomer(1024)
  out.todayItem = (await customers.getToday()).content[0]
  out.followUpItem = (await customers.getFollowUps(1024)).content[0]
  out.callListItem = (await customers.getCallList()).content[0]
  out.noteItem = (await customers.getNotes(1024)).content[0]
  out.notifications = await notifications.getNotifications()
  out.dayCloseItem = (await sales.getDayCloses(1))[0]
  out.gradePolicy = await settings.getGradePolicy()
  out.gradeHistoryItem = (await customers.getGradeHistory(1024)).content[0]
  session.switchAccount(1) // 본사
  out.staffItem = (await stores.getStoreAccounts(1))[0]
  out.periodicRun = await settings.runPeriodic()
  session.switchAccount(2)
  // 쓰기 응답은 새 판매·반품·교환으로 만든다
  out.saleCreated = await sales.createSale(
    {
      sellerStaffId: 17,
      customerId: 1024,
      lines: [{ skuId: 5501, qty: 1, lineAmount: 774060 }],
    },
    'contract-sale-1',
  )
  out.returnCreated = await sales.returnSale(
    out.saleCreated.id,
    { staffId: 17, lines: [{ lineId: 1, qty: 1 }] },
    'contract-ret-1',
  )
  const again = await sales.createSale(
    { sellerStaffId: 17, customerId: 1024, lines: [{ skuId: 5501, qty: 1, lineAmount: 774060 }] },
    'contract-sale-2',
  )
  out.exchangeCreated = await sales.exchangeSale(
    again.id,
    {
      staffId: 17,
      returnLines: [{ lineId: 1, qty: 1 }],
      newLines: [{ skuId: 5502, qty: 1, lineAmount: 800000 }],
    },
    'contract-exc-1',
  )
  return out
}

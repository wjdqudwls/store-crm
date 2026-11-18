import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import MoreView from '@/views/MoreView.vue'
import SaleRecordView from '@/views/SaleRecordView.vue'
import SalesListView from '@/views/SalesListView.vue'
import SaleDetailView from '@/views/SaleDetailView.vue'
import SaleCorrectionView from '@/views/SaleCorrectionView.vue'
import CustomerNewView from '@/views/CustomerNewView.vue'
import CustomerListView from '@/views/CustomerListView.vue'
import CustomerDetailView from '@/views/CustomerDetailView.vue'
import TodayView from '@/views/TodayView.vue'
import CallListView from '@/views/CallListView.vue'
import NotificationsView from '@/views/NotificationsView.vue'
import ProductsView from '@/views/ProductsView.vue'
import ProductFormView from '@/views/ProductFormView.vue'
import StoresView from '@/views/StoresView.vue'
import StoreDetailView from '@/views/StoreDetailView.vue'
import SettingsView from '@/views/SettingsView.vue'
import PasswordView from '@/views/PasswordView.vue'
import NotifySettingsView from '@/views/NotifySettingsView.vue'
import KioskView from '@/views/KioskView.vue'
import JoinView from '@/views/JoinView.vue'
import LoginView from '@/views/LoginView.vue'
import { screens as s, menuFor, allowedPaths } from '@/config/menus'
import { useSessionStore } from '@/stores/session'
import { isPhoneNow } from '@/composables/useIsPhone'
import { showReference } from '@/config/reference'

/** 메뉴의 화면 하나 = 경로 하나. 제목은 폰 위 막대에 보인다 */
const page = (screen, component) => ({
  path: screen.path,
  component,
  meta: { title: screen.label },
})

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        page(s.saleNew, SaleRecordView),
        page(s.sales, SalesListView),
        {
          path: '/sales/:id(\\d+)',
          component: SaleDetailView,
          // 본사는 매장 상세의 판매 내역에서 같은 화면을 연다 (05 SCR-03)
          meta: { title: '영수증 상세', allowedAs: [s.sales.path, s.stores.path] },
        },
        {
          path: '/sales/:id(\\d+)/edit',
          component: SaleCorrectionView,
          // 판매 기록의 정정 모드 (05 SCR-02). 본사는 마감 뒤에도 고친다
          meta: { title: '판매 정정', allowedAs: [s.sales.path, s.stores.path], reference: true },
        },
        page(s.customers, CustomerListView),
        page(s.customerNew, CustomerNewView),
        {
          path: '/customers/:id(\\d+)',
          component: CustomerDetailView,
          meta: { title: '고객 상세', allowedAs: [s.customers.path] },
        },
        page(s.today, TodayView),
        page(s.callList, CallListView),
        page(s.notifications, NotificationsView),
        page(s.products, ProductsView),
        {
          path: '/products/new',
          component: ProductFormView,
          meta: { title: '상품 등록', allowedAs: [s.products.path] },
        },
        {
          path: '/products/:id(\\d+)',
          component: ProductFormView,
          meta: { title: '상품 수정', allowedAs: [s.products.path] },
        },
        page(s.stores, StoresView),
        {
          path: '/hq/stores/:id(\\d+)',
          component: StoreDetailView,
          meta: { title: '매장 상세', allowedAs: [s.stores.path] },
        },
        { ...page(s.gradeView, SettingsView), meta: { title: '기준·설정' } },
        page(s.password, PasswordView),
        page(s.notifySettings, NotifySettingsView),
        { path: '/more', component: MoreView, meta: { title: '더보기' } },
      ],
    },
    // 레이아웃 밖: 키오스크는 메뉴가 없고, QR·로그인은 로그인 없이 연다
    { path: s.kiosk.path, component: KioskView, meta: { title: s.kiosk.label, reference: true } },
    {
      path: '/join',
      component: JoinView,
      meta: { title: '고객 등록', public: true, reference: true },
    },
    { path: '/login', component: LoginView, meta: { title: '로그인', public: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/** 첫 화면: 같은 매장 계정이라도 PC면 판매 기록, 폰이면 오늘 할 일 (05 1.1) */
export function firstScreenPath(role, isPhone) {
  const m = menuFor(role)
  return (isPhone ? m.firstPhone : m.firstPc).path
}

router.beforeEach((to) => {
  const { me } = useSessionStore()
  const first = firstScreenPath(me.role, isPhoneNow())
  // 참고 화면은 실서버에서 첫 화면으로 보낸다 (QR 공개 페이지도 같다)
  if (!showReference && to.meta.reference) return first
  if (to.meta.public) return
  if (to.path === '/') return first
  // 그 계정의 메뉴에 없는 화면은 첫 화면으로 보낸다 (05 1.2)
  // 상세처럼 주소에 ID가 든 화면은 목록 화면의 권한을 따른다 (meta.allowedAs)
  const allowed = allowedPaths(me.role)
  const keys = to.meta.allowedAs ?? [to.path]
  if (!keys.some((k) => allowed.has(k))) return first
})

export default router

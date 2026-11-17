import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import MoreView from '@/views/MoreView.vue'
import SaleRecordView from '@/views/SaleRecordView.vue'
import CustomerNewView from '@/views/CustomerNewView.vue'
import CustomerListView from '@/views/CustomerListView.vue'
import CustomerDetailView from '@/views/CustomerDetailView.vue'
import { screens, menuFor, allowedPaths } from '@/config/menus'
import { useSessionStore } from '@/stores/session'
import { isPhoneNow } from '@/composables/useIsPhone'

/** 아직 만들지 않은 화면은 자리만 둔다. 화면을 만들면 component를 바꾼다 */
const placeholder = (screen) => ({
  path: screen.path,
  component: PlaceholderView,
  props: { title: screen.label, task: screen.task },
  meta: { title: screen.label },
})

const uniqueScreens = [...new Map(Object.values(screens).map((x) => [x.path, x])).values()]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: screens.saleNew.path,
          component: SaleRecordView,
          meta: { title: screens.saleNew.label },
        },
        {
          path: screens.customerNew.path,
          component: CustomerNewView,
          meta: { title: screens.customerNew.label },
        },
        {
          path: screens.customers.path,
          component: CustomerListView,
          meta: { title: screens.customers.label },
        },
        {
          path: '/customers/:id(\\d+)',
          component: CustomerDetailView,
          meta: { title: '고객 상세', allowedAs: screens.customers.path },
        },
        ...uniqueScreens
          .filter((x) => ![screens.saleNew, screens.customerNew, screens.customers].includes(x))
          .map(placeholder),
        { path: '/more', component: MoreView, meta: { title: '더보기' } },
      ],
    },
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
  if (to.path === '/') return first
  // 그 계정의 메뉴에 없는 화면은 첫 화면으로 보낸다 (05 1.2)
  // 상세처럼 주소에 ID가 든 화면은 목록 화면의 권한을 따른다 (meta.allowedAs)
  if (!allowedPaths(me.role).has(to.meta.allowedAs ?? to.path)) return first
})

export default router

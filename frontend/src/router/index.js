import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import MoreView from '@/views/MoreView.vue'
import SaleRecordView from '@/views/SaleRecordView.vue'
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
        ...uniqueScreens.filter((x) => x !== screens.saleNew).map(placeholder),
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
  if (!allowedPaths(me.role).has(to.path)) return first
})

export default router

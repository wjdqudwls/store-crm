<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { useIsPhone } from '@/composables/useIsPhone'
import { menuFor, accountLabel } from '@/config/menus'
import DevAccountSwitch from '@/components/DevAccountSwitch.vue'
import { useNotificationStore } from '@/stores/notifications'

/**
 * PC: 위 막대 한 줄(서비스 이름 · 메뉴 · 계정), 본문 최대 1280px.
 * 폰: 위 막대(화면 이름 · 계정 줄임), 아래 탭 4개 (05 1.1).
 */
const session = useSessionStore()
const route = useRoute()
const isPhone = useIsPhone()

const menu = computed(() => menuFor(session.me.role))
const screenTitle = computed(() => route.meta.title ?? 'CRM pos')

const tabs = computed(() => {
  const m = menu.value
  return m.phoneMore.length ? [...m.phoneTabs, { path: '/more', label: '더보기' }] : m.phoneTabs
})

// 안 읽은 알림 수: 직원만, 화면을 열거나 옮길 때 다시 받는다 (05 SCR-07 W6)
const notices = useNotificationStore()
const isEmployee = computed(() => !['STORE', 'HQ', 'KIOSK'].includes(session.me.role))
watch(
  () => [route.path, session.me.id],
  () => {
    if (isEmployee.value) notices.refresh()
  },
  { immediate: true },
)

// 계정 메뉴 (05 1.2, F13 초안): 비밀번호 바꾸기는 개인 계정만
const router = useRouter()
function onAccount(cmd) {
  if (cmd === 'password') router.push('/me/password')
  else if (cmd === 'notify') router.push('/me/notifications')
  else if (cmd === 'logout') router.push('/login')
}

function isActive(path) {
  if (path === '/more')
    return route.path === '/more' || menu.value.phoneMore.some((x) => x.path === route.path)
  // 판매 기록(/sales/new)은 판매 내역(/sales) 메뉴에 불이 들어오지 않게 한다
  if (path === '/sales') return route.path === path || /^\/sales\/\d+/.test(route.path)
  return route.path === path || route.path.startsWith(path + '/')
}
</script>

<template>
  <div class="app" :class="isPhone ? 'app--phone' : 'app--pc'">
    <header class="topbar">
      <template v-if="!isPhone">
        <div class="topbar__inner">
          <RouterLink to="/" class="topbar__brand">CRM pos</RouterLink>
          <nav class="topbar__menu" aria-label="메뉴">
            <RouterLink
              v-for="item in menu.pc"
              :key="item.label"
              :to="item.path"
              class="topbar__link"
              :class="{ 'is-active': isActive(item.path) }"
            >
              <el-badge
                v-if="item.path === '/notifications' && notices.unreadCount"
                :value="notices.unreadCount"
              >
                {{ item.label }}
              </el-badge>
              <template v-else>{{ item.label }}</template>
            </RouterLink>
          </nav>
          <div class="topbar__account">
            <DevAccountSwitch />
            <el-dropdown trigger="click" @command="onAccount">
              <button type="button" class="topbar__me">{{ accountLabel(session.me) }} ▾</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="isEmployee" command="notify">알림 설정</el-dropdown-item>
                  <el-dropdown-item v-if="isEmployee || session.isHq" command="password">
                    비밀번호 바꾸기
                  </el-dropdown-item>
                  <el-dropdown-item command="logout">로그아웃</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="topbar__inner">
          <h1 class="topbar__title">{{ screenTitle }}</h1>
          <DevAccountSwitch />
        </div>
      </template>
    </header>

    <main class="content">
      <!-- 데모 계정을 바꾸면 화면을 새로 그린다 (같은 화면이어도 그 계정의 데이터로) -->
      <RouterView :key="session.me.id" />
    </main>

    <nav v-if="isPhone" class="tabs" aria-label="아래 탭">
      <RouterLink
        v-for="tab in tabs"
        :key="tab.label"
        :to="tab.path"
        class="tabs__item"
        :class="{ 'is-active': isActive(tab.path) }"
      >
        <el-badge
          v-if="tab.path === '/notifications'"
          :value="notices.unreadCount"
          :hidden="!notices.unreadCount"
          class="tabs__badge"
        >
          {{ tab.label }}
        </el-badge>
        <template v-else>{{ tab.label }}</template>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--color-topbar);
  color: var(--color-topbar-text);
  border-bottom: 1px solid var(--color-border);
}
.topbar__inner {
  max-width: var(--content-max-width);
  margin: 0 auto;
  height: 56px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.app--phone .topbar__inner {
  height: 52px;
  gap: 8px;
  justify-content: space-between;
}
.topbar__brand {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-topbar-text);
  text-decoration: none;
  white-space: nowrap;
}
.topbar__title {
  font-size: var(--font-size-lg);
  white-space: nowrap;
}
.topbar__menu {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 4px;
  min-width: 0;
}
.topbar__link {
  padding: 16px 10px;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
}
.topbar__link:hover {
  color: var(--color-text);
}
.topbar__link.is-active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
  border-bottom-color: var(--color-primary);
}
.topbar__account {
  display: flex;
  align-items: center;
  gap: 12px;
}
.topbar__me {
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
  color: var(--color-topbar-text);
  background: none;
  border: 0;
  padding: 4px 0;
  cursor: pointer;
}

.content {
  flex: 1;
  width: 100%;
  max-width: var(--content-max-width);
  margin: 0 auto;
  padding: 24px 16px;
}
.app--phone .content {
  padding: 16px 16px calc(72px + env(safe-area-inset-bottom));
}

.tabs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  padding-bottom: env(safe-area-inset-bottom);
}
.tabs__item {
  min-height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
  text-decoration: none;
}
.tabs__item.is-active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
</style>

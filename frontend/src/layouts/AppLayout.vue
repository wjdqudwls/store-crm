<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { useIsPhone } from '@/composables/useIsPhone'
import { menuFor, accountLabel } from '@/config/menus'
import DevAccountSwitch from '@/components/DevAccountSwitch.vue'

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

/** 알림 개수 (가짜 데이터, F10에서 실제로) */
const unreadCount = 2

function isActive(path) {
  if (path === '/more')
    return route.path === '/more' || menu.value.phoneMore.some((x) => x.path === route.path)
  return route.path === path || (path !== '/sales' && route.path.startsWith(path + '/'))
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
              {{ item.label }}
            </RouterLink>
          </nav>
          <div class="topbar__account">
            <DevAccountSwitch />
            <span class="topbar__me">{{ accountLabel(session.me) }}</span>
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
      <RouterView />
    </main>

    <nav v-if="isPhone" class="tabs" aria-label="아래 탭">
      <RouterLink
        v-for="tab in tabs"
        :key="tab.label"
        :to="tab.path"
        class="tabs__item"
        :class="{ 'is-active': isActive(tab.path) }"
      >
        <el-badge v-if="tab.path === '/notifications'" :value="unreadCount" class="tabs__badge">
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
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
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

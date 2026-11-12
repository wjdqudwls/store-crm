<script setup>
import { computed } from 'vue'
import { useSessionStore } from '@/stores/session'
import { menuFor } from '@/config/menus'

/** 폰 아래 탭의 "더보기" (05 1.2) */
const session = useSessionStore()
const items = computed(() => menuFor(session.me.role).phoneMore)
</script>

<template>
  <ul class="more">
    <li v-for="item in items" :key="item.label">
      <RouterLink :to="item.path" class="more__link">{{ item.label }}</RouterLink>
    </li>
  </ul>
</template>

<style scoped>
.more {
  list-style: none;
  margin: 0;
  padding: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}
.more li + li {
  border-top: 1px solid var(--color-border);
}
.more__link {
  display: flex;
  align-items: center;
  min-height: var(--touch-min);
  padding: 0 16px;
  color: var(--color-text);
  text-decoration: none;
}
</style>

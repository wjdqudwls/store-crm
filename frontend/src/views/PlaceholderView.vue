<script setup>
import { useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'

/** 아직 만들지 않은 화면 자리. 각 화면 작업(F1 등)에서 바꾼다 */
defineProps({ title: { type: String, required: true }, task: { type: String, required: true } })
const route = useRoute()
const session = useSessionStore()
</script>

<template>
  <section class="placeholder">
    <h2 class="placeholder__title">{{ title }}</h2>
    <p class="placeholder__text">{{ task }}에서 만듭니다.</p>
    <!-- 고객 메뉴의 [고객 등록] (SCR-04). 고객 목록(F2)을 만들면 그 화면으로 옮긴다 -->
    <el-button
      v-if="route.path === '/customers' && session.isStore"
      type="primary"
      class="placeholder__action"
      @click="$router.push('/customers/new')"
      >고객 등록</el-button
    >
  </section>
</template>

<style scoped>
.placeholder {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 24px;
}
.placeholder__title {
  font-size: var(--font-size-xl);
}
.placeholder__text {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.placeholder__action {
  margin-top: 16px;
}
</style>

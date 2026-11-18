<script setup>
import { showReference } from '@/config/reference'
</script>

<template>
  <p v-if="showReference" class="ref-banner" role="note">
    참고 화면 · 서버 없음 (데모에서만 보이고, 저장해도 실제 서버에는 남지 않습니다)
  </p>
</template>

<style scoped>
.ref-banner {
  margin: 0 0 12px;
  padding: 6px 12px;
  border: 1px dashed var(--color-warning);
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--color-warning);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}
</style>

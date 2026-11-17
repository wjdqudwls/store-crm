<script setup>
/** 아직 만들지 않은 화면 자리. 각 화면 작업(F1 등)에서 바꾼다 */
defineProps({ title: { type: String, required: true }, task: { type: String, required: true } })
</script>

<template>
  <section class="placeholder">
    <h2 class="placeholder__title">{{ title }}</h2>
    <p class="placeholder__text">{{ task }}에서 만듭니다.</p>
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
</style>

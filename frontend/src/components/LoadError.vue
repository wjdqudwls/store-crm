<script setup>
/**
 * 화면을 불러오지 못했을 때의 띠 (05 1.5). 스켈레톤 자리에 둔다.
 * `error`는 `useLoadError().error` 값, 다시 시도는 `retry`로 알린다.
 */
defineProps({
  error: { type: Object, default: null },
  busy: { type: Boolean, default: false },
})
defineEmits(['retry'])
</script>

<template>
  <div v-if="error" class="load-error" role="alert">
    <div class="load-error__text">
      <strong>불러오지 못했습니다</strong>
      <span>{{ error.message }}</span>
    </div>
    <el-button size="small" :loading="busy" @click="$emit('retry')">다시 시도</el-button>
  </div>
</template>

<style scoped>
.load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 6px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: var(--font-size-sm);
}
.load-error__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.load-error__text span {
  color: var(--color-text);
}
</style>

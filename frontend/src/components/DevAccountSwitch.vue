<script setup>
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { accountLabel } from '@/config/menus'

/** 개발·데모용 계정 바꾸기. 11단계(F13) 로그인을 만들면 지운다 (05 1.2, W13) */
const session = useSessionStore()
const router = useRouter()

function onChange(id) {
  session.switchAccount(id)
  router.push('/')
}
</script>

<template>
  <label class="dev-switch">
    <span class="dev-switch__label">데모 계정</span>
    <el-select
      :model-value="session.me.id"
      size="small"
      class="dev-switch__select"
      aria-label="데모 계정 바꾸기"
      @change="onChange"
    >
      <el-option v-for="a in session.accounts" :key="a.id" :label="accountLabel(a)" :value="a.id" />
    </el-select>
  </label>
</template>

<style scoped>
.dev-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 6px;
  border: 1px dashed var(--color-warning);
  border-radius: 6px;
}
.dev-switch__label {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-warning);
  white-space: nowrap;
}
.dev-switch__select {
  width: 150px;
}
</style>

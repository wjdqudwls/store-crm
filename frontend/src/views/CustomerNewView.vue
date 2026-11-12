<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CustomerForm from '@/components/CustomerForm.vue'

/**
 * 고객 등록 화면 `/customers/new` (05 SCR-04, 매장 계정).
 * 등록 뒤에는 고객 상세로 간다. 고객 상세(F2)를 만들기 전에는 고객 화면으로 간다.
 */
const router = useRouter()
const formKey = ref(0)

function onCreated(res) {
  ElMessage.success({
    message: `${res.name}님을 고객으로 등록했습니다 · ${res.phoneMasked}`,
    duration: 4000,
  })
  router.push('/customers')
}

function onExisting() {
  router.push('/customers')
}
</script>

<template>
  <div class="cnew">
    <header class="cnew__head">
      <h1 class="cnew__title">고객 등록</h1>
      <p class="cnew__desc">
        종이 고객등록카드 대신 직원이 입력합니다. 필수 칸만 채워도 등록됩니다.
      </p>
    </header>
    <div class="cnew__card">
      <CustomerForm :key="formKey" @created="onCreated" @use-existing="onExisting" />
    </div>
  </div>
</template>

<style scoped>
.cnew {
  max-width: 760px;
  margin: 0 auto;
}
.cnew__head {
  margin-bottom: 16px;
}
.cnew__title {
  margin: 0 0 4px;
  font-size: var(--font-size-xl);
}
.cnew__desc {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.cnew__card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 20px;
}
@media (max-width: 767.98px) {
  /* 폰은 위 막대가 화면 이름을 보여준다 */
  .cnew__title {
    display: none;
  }
  .cnew__card {
    padding: 16px;
  }
}
</style>

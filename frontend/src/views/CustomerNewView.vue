<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CustomerForm from '@/components/CustomerForm.vue'
import { useIsPhone } from '@/composables/useIsPhone'

/**
 * 고객 등록 화면 `/customers/new` (05 SCR-04, 매장 계정).
 * PC는 기존 매장 POS처럼 화면 제목 오른쪽 위에 [신규] [등록]을 둔다. 폰은 폼 아래 버튼.
 * 등록 뒤에는 고객 상세로 간다. 같은 번호가 있으면 기존 고객 상세로 (REG-05).
 */
const router = useRouter()
const isPhone = useIsPhone()
const formKey = ref(0)
const formRef = ref()
const saving = computed(() => formRef.value?.saving ?? false)

function onCreated(res) {
  ElMessage.success({
    message: `${res.name}님을 고객으로 등록했습니다 · ${res.phoneMasked}`,
    duration: 4000,
  })
  router.push(`/customers/${res.id}`)
}

function onExisting(customerId) {
  router.push(`/customers/${customerId}`)
}

/** [신규]: 입력한 칸을 모두 비운다 */
function reset() {
  formKey.value += 1
}
</script>

<template>
  <div class="page cnew">
    <header class="page__head">
      <div>
        <h1 class="page__title">고객 등록</h1>
        <p class="page__sub">
          종이 고객등록카드 대신 직원이 입력합니다. 필수 칸만 채워도 등록됩니다.
        </p>
      </div>
      <div v-if="!isPhone" class="page__actions">
        <el-button @click="reset">신규</el-button>
        <el-button type="primary" :loading="saving" @click="formRef?.submit()">등록</el-button>
      </div>
    </header>
    <div class="card">
      <CustomerForm
        ref="formRef"
        :key="formKey"
        :hide-actions="!isPhone"
        @created="onCreated"
        @use-existing="onExisting"
      />
    </div>
  </div>
</template>

<style scoped>
.cnew {
  max-width: 1000px;
}
</style>

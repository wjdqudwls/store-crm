<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CustomerForm from '@/components/CustomerForm.vue'
import { getJoinInfo, publicRegister } from '@/api/public'

/**
 * QR 등록 `/join?store=GN01&staff=S017` (05 SCR-01, REG-03). 로그인 없이 고객 폰에서 연다.
 * 기존 번호여도 같은 완료 문구를 보여 가입 여부를 드러내지 않는다 (REG-05).
 */
const route = useRoute()
const info = ref(null)
const invalid = ref(false)
const done = ref(false)
const tooMany = ref(false)

onMounted(async () => {
  try {
    info.value = await getJoinInfo(
      String(route.query.store ?? ''),
      route.query.staff ? String(route.query.staff) : undefined,
    )
  } catch (e) {
    if (e?.status === 404) invalid.value = true
    else if (e?.status === 429) tooMany.value = true
  }
})

/** 매장·직원 코드는 QR 주소에서 붙인다. 직원 코드가 틀려도 등록은 받는다 (REG-03) */
function submit(body) {
  return publicRegister({ ...body, storeCode: route.query.store, staffCode: route.query.staff })
}
</script>

<template>
  <div class="join">
    <p v-if="invalid" class="join__msg">QR 코드가 올바르지 않습니다. 직원에게 문의해 주세요.</p>
    <p v-else-if="tooMany" class="join__msg">잠시 후 다시 시도해 주세요.</p>
    <el-skeleton v-else-if="!info" animated :rows="6" class="join__card" />
    <section v-else-if="done" class="join__card join__done">
      <h1>등록이 완료되었습니다</h1>
      <p class="muted">이 창을 닫아 주세요.</p>
    </section>
    <section v-else class="join__card">
      <h1 class="join__title">{{ info.store.name }} 고객 등록</h1>
      <p class="muted join__desc">등록하면 구매할 때 매장 단골 등급 할인을 받을 수 있어요.</p>
      <CustomerForm
        :show-staff="false"
        :collapse-optional="false"
        lock-until-consent
        :submit-fn="submit"
        :store-name="info.store.name"
        :consents="info.consents"
        @created="done = true"
      />
    </section>
  </div>
</template>

<style scoped>
.join {
  min-height: 100vh;
  background: var(--color-bg);
  padding: 16px;
}
.join__card {
  max-width: 640px;
  margin: 0 auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 20px 16px;
}
.join__title {
  font-size: var(--font-size-xl);
}
.join__desc {
  margin: 4px 0 16px;
}
.join__done {
  text-align: center;
  padding: 48px 16px;
}
.join__msg {
  max-width: 480px;
  margin: 80px auto;
  text-align: center;
}
</style>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import CustomerForm from '@/components/CustomerForm.vue'
import DevAccountSwitch from '@/components/DevAccountSwitch.vue'
import { publicRegister } from '@/api/public'
import { useSessionStore } from '@/stores/session'

/**
 * 키오스크 등록 `/kiosk` (05 SCR-01, REG-02). 매장 iPad에서 고객이 직접 입력한다.
 * 메뉴·로그아웃이 없고, 완료 5초 뒤나 2분 동안 손대지 않으면 입력을 지우고 첫 화면으로 간다 (W1).
 */
const session = useSessionStore()
const step = ref('start') // start | form | done
const formKey = ref(0)

const IDLE_MS = 2 * 60 * 1000
let idleTimer = null
let doneTimer = null

function reset() {
  clearTimeout(idleTimer)
  clearTimeout(doneTimer)
  formKey.value += 1
  step.value = 'start'
}
function touch() {
  if (step.value !== 'form') return
  clearTimeout(idleTimer)
  idleTimer = setTimeout(reset, IDLE_MS)
}
function start() {
  step.value = 'form'
  touch()
}
function done() {
  clearTimeout(idleTimer)
  step.value = 'done'
  doneTimer = setTimeout(reset, 5000)
}

const events = ['pointerdown', 'keydown', 'input']
onMounted(() => events.forEach((e) => window.addEventListener(e, touch, true)))
onBeforeUnmount(() => {
  events.forEach((e) => window.removeEventListener(e, touch, true))
  clearTimeout(idleTimer)
  clearTimeout(doneTimer)
})
</script>

<template>
  <div class="kiosk size-kiosk">
    <div class="kiosk__dev"><DevAccountSwitch /></div>

    <section v-if="step === 'start'" class="kiosk__start">
      <p class="kiosk__store">{{ session.me.storeName }}</p>
      <h1 class="kiosk__title">매장 단골 고객 등록</h1>
      <p class="kiosk__desc">등록하면 구매할 때 매장 단골 등급 할인을 받을 수 있어요.</p>
      <el-button type="primary" size="large" class="kiosk__go" @click="start">시작하기</el-button>
    </section>

    <section v-else-if="step === 'form'" class="kiosk__form">
      <header class="kiosk__form-head">
        <h1>{{ session.me.storeName }} 고객 등록</h1>
        <el-button text @click="reset">처음으로</el-button>
      </header>
      <CustomerForm
        :key="formKey"
        :show-staff="false"
        :collapse-optional="false"
        lock-until-consent
        :submit-fn="publicRegister"
        :store-name="session.me.storeName"
        @created="done"
      />
    </section>

    <section v-else class="kiosk__start">
      <h1 class="kiosk__title">등록이 완료되었습니다</h1>
      <p class="kiosk__desc">감사합니다. 잠시 뒤 첫 화면으로 돌아갑니다.</p>
    </section>
  </div>
</template>

<style scoped>
.kiosk {
  min-height: 100vh;
  background: var(--color-bg);
  padding: 24px;
  position: relative;
}
.kiosk__dev {
  position: absolute;
  top: 12px;
  right: 12px;
}
.kiosk__start {
  min-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
}
.kiosk__store {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-lg);
}
.kiosk__title {
  font-size: var(--font-size-2xl);
}
.kiosk__desc {
  margin: 0;
  color: var(--color-text-muted);
}
.kiosk__go {
  min-width: 240px;
  height: 64px;
  font-size: var(--font-size-xl);
}
.kiosk__form {
  max-width: 720px;
  margin: 48px auto 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 24px;
}
.kiosk__form-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.kiosk__form-head h1 {
  font-size: var(--font-size-xl);
}
.kiosk :deep(.el-button),
.kiosk :deep(.el-input__wrapper) {
  min-height: 48px;
}
</style>

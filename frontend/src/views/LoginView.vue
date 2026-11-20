<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { menuFor } from '@/config/menus'
import { isPhoneNow } from '@/composables/useIsPhone'
import { loginErrorMessage } from '@/features/auth/loginError'
import { isFake } from '@/api/mode'

/**
 * 로그인 `/login` (05 SCR-10, AUTH-01·03). 실서버는 JWT 로그인, 데모(가짜)는 아이디만 맞으면 들어간다.
 * 같은 아이디를 5번 틀리면 10분 잠긴다(429 AUTH_LOGIN_LOCKED, 03 5장).
 */
const router = useRouter()
const session = useSessionStore()
const form = reactive({ loginId: '', password: '' })
const error = ref('')
const loading = ref(false)

async function login() {
  error.value = ''
  if (!form.loginId.trim() || !form.password) {
    error.value = '아이디와 비밀번호를 입력해 주세요'
    return
  }
  loading.value = true
  try {
    await session.login(form.loginId.trim(), form.password)
  } catch (e) {
    error.value = loginErrorMessage(e)
    return
  } finally {
    loading.value = false
  }
  const m = menuFor(session.me.role)
  router.push((isPhoneNow() ? m.firstPhone : m.firstPc).path)
}
</script>

<template>
  <div class="login">
    <form class="login__card" @submit.prevent="login">
      <h1 class="login__brand">CRM pos</h1>
      <p class="muted">매장 단골 고객관리</p>
      <el-input v-model="form.loginId" size="large" placeholder="아이디" autocomplete="username" />
      <el-input
        v-model="form.password"
        size="large"
        type="password"
        show-password
        placeholder="비밀번호"
        autocomplete="current-password"
      />
      <p v-if="error" class="field-error">{{ error }}</p>
      <el-button type="primary" size="large" native-type="submit" :loading="loading"
        >로그인</el-button
      >
      <p v-if="isFake" class="draft-note">
        초안(데모): 아이디 gn01 · gn01-jieun · hq-yuna · gn01-kiosk. 비밀번호는 11단계 전까지
        확인하지 않습니다.
      </p>
    </form>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  padding: 16px;
}
.login__card {
  width: min(380px, 100%);
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 32px 24px;
}
.login__brand {
  font-size: var(--font-size-2xl);
}
.login__card .muted {
  margin: 0 0 8px;
}
.login__card .el-button {
  margin-top: 4px;
}
</style>

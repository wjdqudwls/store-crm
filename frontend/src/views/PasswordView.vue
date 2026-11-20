<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSessionStore } from '@/stores/session'
import { isFake } from '@/api/mode'
import { changePassword } from '@/api/auth'
import { setTokens } from '@/api/tokens'
import { passwordErrors } from '@/features/auth/passwordError'

/**
 * 비밀번호 바꾸기 `/me/password` (05 SCR-10, AUTH-04). 개인 계정(직원·본사)만.
 * 실서버는 `PATCH /me/password`(성공하면 새 토큰이 와서 저장한다, 다른 기기는 로그아웃), 데모는 규칙만 확인한다.
 */
const router = useRouter()
const session = useSessionStore()
const saving = ref(false)
const form = reactive({ current: '', next: '', confirm: '' })
const errors = ref({})

async function save() {
  const e = {}
  if (!form.current) e.current = '현재 비밀번호를 입력해 주세요'
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(form.next))
    e.next = '8자 이상, 영문과 숫자를 섞어 주세요'
  if (form.next !== form.confirm) e.confirm = '새 비밀번호가 서로 다릅니다'
  errors.value = e
  if (Object.keys(e).length) return
  if (!isFake) {
    saving.value = true
    try {
      setTokens(await changePassword(form.current, form.next))
    } catch (err) {
      errors.value = passwordErrors(err)
      return
    } finally {
      saving.value = false
    }
  }
  ElMessage.success('비밀번호를 바꿨습니다. 다른 기기의 로그인이 끊겼습니다')
  router.back()
}

async function logoutAll() {
  try {
    await ElMessageBox.confirm('모든 기기에서 로그아웃할까요?', '모든 기기에서 로그아웃', {
      confirmButtonText: '로그아웃',
      cancelButtonText: '돌아가기',
    })
  } catch {
    return
  }
  try {
    await session.logoutAll()
  } catch (err) {
    ElMessage.error(err?.body?.message ?? '로그아웃하지 못했습니다. 다시 눌러 주세요')
    return
  }
  router.push('/login')
}
</script>

<template>
  <div class="page page--narrow">
    <header class="page__head">
      <h1 class="page__title">비밀번호 바꾸기</h1>
    </header>
    <form class="card pw" @submit.prevent="save">
      <el-form label-position="top">
        <el-form-item label="현재 비밀번호" :error="errors.current">
          <el-input
            v-model="form.current"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="새 비밀번호 (8자 이상, 영문과 숫자)" :error="errors.next">
          <el-input v-model="form.next" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="새 비밀번호 확인" :error="errors.confirm">
          <el-input
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
      </el-form>
      <div class="pw__actions">
        <el-button text type="danger" @click="logoutAll">모든 기기에서 로그아웃</el-button>
        <el-button type="primary" native-type="submit" :loading="saving">바꾸기</el-button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.pw__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

// 가짜 계정은 데모·개발(fake)에서만 불러온다. 실서버 빌드에는 들어가지 않는다 (api/mode.js)
const fakeAccounts =
  import.meta.env.VITE_API_MODE !== 'real' ? (await import('@/mocks/accounts')).fakeAccounts : []

/** 실서버에서 로그인(F13) 전의 빈 계정. 로그인하면 `GET /me` 값으로 바뀐다 */
const anonymous = {
  id: null,
  name: '',
  role: null,
  managerSlot: null,
  storeId: null,
  storeCode: null,
  storeName: null,
  loginId: null,
}

/**
 * 로그인한 계정. 11단계(F13) 전에는 가짜 계정을 고른다 (ADR-032).
 * 화면 메모리에만 두고 브라우저 저장소에는 넣지 않는다 (NFR-04c).
 */
export const useSessionStore = defineStore('session', () => {
  const me = ref(fakeAccounts[0] ?? anonymous)
  const accounts = fakeAccounts

  const isStore = computed(() => me.value.role === 'STORE')
  const isHq = computed(() => me.value.role === 'HQ')

  function switchAccount(id) {
    const next = accounts.find((a) => a.id === id)
    if (next) me.value = next
  }

  return { me, accounts, isStore, isHq, switchAccount }
})

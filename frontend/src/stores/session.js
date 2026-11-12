import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fakeAccounts } from '@/mocks/accounts'

/**
 * 로그인한 계정. 11단계(F13) 전에는 가짜 계정을 고른다 (ADR-032).
 * 화면 메모리에만 두고 브라우저 저장소에는 넣지 않는다 (NFR-04c).
 */
export const useSessionStore = defineStore('session', () => {
  const me = ref(fakeAccounts[0])
  const accounts = fakeAccounts

  const isStore = computed(() => me.value.role === 'STORE')
  const isHq = computed(() => me.value.role === 'HQ')

  function switchAccount(id) {
    const next = accounts.find((a) => a.id === id)
    if (next) me.value = next
  }

  return { me, accounts, isStore, isHq, switchAccount }
})

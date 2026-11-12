import { onBeforeUnmount, ref } from 'vue'

/** 화면 폭 768px 미만이면 폰 레이아웃 (05 1.1) */
export const PHONE_QUERY = '(max-width: 767.98px)'

export function isPhoneNow() {
  return typeof window !== 'undefined' && window.matchMedia(PHONE_QUERY).matches
}

export function useIsPhone() {
  const isPhone = ref(isPhoneNow())
  if (typeof window === 'undefined') return isPhone

  const mql = window.matchMedia(PHONE_QUERY)
  const onChange = (e) => (isPhone.value = e.matches)
  mql.addEventListener('change', onChange)
  onBeforeUnmount(() => mql.removeEventListener('change', onChange))
  return isPhone
}

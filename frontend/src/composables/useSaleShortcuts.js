import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 판매 기록 화면의 키 (05 1.3 저장 확인 창): Ctrl+Enter = 저장 확인 창 열기, 확인 창에서 Enter = 저장.
 * 구매·환불·교환·정정이 같이 쓴다.
 *
 * @param {{ confirmOpen: import('vue').Ref<boolean>, onSave: () => void, onRequest: () => void,
 *   blocked?: () => boolean }} p blocked가 true면(다른 창이 열려 있을 때 등) 키를 무시한다
 */
export function useSaleShortcuts({ confirmOpen, onSave, onRequest, blocked = () => false }) {
  function onKeydown(e) {
    if (blocked()) return
    if (confirmOpen.value && e.key === 'Enter' && !e.isComposing) {
      e.preventDefault()
      onSave()
    } else if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault()
      onRequest()
    }
  }
  onMounted(() => window.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
}

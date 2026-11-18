import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getNotifications } from '@/api/notifications'

/** 안 읽은 알림 수 (아래 탭·메뉴 배지). 화면을 열거나 옮길 때 다시 받는다 (05 SCR-07 W6) */
export const useNotificationStore = defineStore('notifications', () => {
  const unreadCount = ref(0)
  /** 배지는 보조 표시라 실패해도 화면을 막지 않고 숫자를 그대로 둔다 (알림함 화면에서 오류 띠로 알린다) */
  async function refresh() {
    try {
      unreadCount.value = (await getNotifications()).unreadCount
    } catch {
      // 숫자를 그대로 둔다
    }
  }
  return { unreadCount, refresh }
})

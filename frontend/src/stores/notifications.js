import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getNotifications } from '@/api/notifications'

/** 안 읽은 알림 수 (아래 탭·메뉴 배지). 화면을 열거나 옮길 때 다시 받는다 (05 SCR-07 W6) */
export const useNotificationStore = defineStore('notifications', () => {
  const unreadCount = ref(0)
  async function refresh() {
    unreadCount.value = (await getNotifications()).unreadCount
  }
  return { unreadCount, refresh }
})

<script setup>
import { onMounted, ref } from 'vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import { useRouter } from 'vue-router'
import { getNotifications, markRead } from '@/api/notifications'
import { useNotificationStore } from '@/stores/notifications'
import { dateTimeLabel } from '@/features/customer/customerDetail'

/**
 * 알림함 `/notifications` (05 SCR-07, CAL-09, 직원만). 안 읽은 것 먼저(굵게).
 * 누르면 읽음 표시하고 고객 상세 메모 탭으로 간다.
 */
const router = useRouter()
const notices = useNotificationStore()
const list = ref(null)

const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    const res = await getNotifications()
    list.value = res.content
    notices.unreadCount = res.unreadCount
  })
onMounted(load)

const typeLabels = {
  FOLLOW_UP: '새 할 일',
  NOTE: '담당 고객의 새 메모',
  VISIT: '방문 예정',
  RECALL: '다시 연락',
}

async function open(n) {
  if (!n.read) {
    await markRead(n.id)
    notices.unreadCount = Math.max(0, notices.unreadCount - 1)
  }
  // 여러 고객을 묶은 알림은 오늘 할 일로 간다
  if (n.customerId == null) router.push('/today')
  else router.push({ path: `/customers/${n.customerId}`, query: { from: 'notifications' } })
}
</script>

<template>
  <div class="page page--narrow">
    <header class="page__head">
      <h1 class="page__title">알림함</h1>
      <div class="page__actions">
        <el-button size="small" @click="router.push('/me/notifications')">알림 설정</el-button>
      </div>
    </header>
    <LoadError :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-if="!list && !error" animated :rows="3" class="card" />
    <p v-else-if="list && !list.length" class="card empty">알림이 없습니다.</p>
    <ul v-else-if="list" class="nt card">
      <li v-for="n in list" :key="n.id">
        <button
          type="button"
          class="nt__row"
          :class="{ 'nt__row--unread': !n.read }"
          @click="open(n)"
        >
          <span class="nt__dot" aria-hidden="true" />
          <span class="nt__body">
            <span class="nt__type">{{ typeLabels[n.type] }}</span>
            <span class="nt__msg">{{ n.message }}</span>
          </span>
          <span class="muted">{{ dateTimeLabel(n.createdAt) }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.nt {
  list-style: none;
  margin: 0;
  padding: 0;
}
.nt li + li {
  border-top: 1px solid var(--color-border);
}
.nt__row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: 0;
  background: none;
  font: inherit;
  color: var(--color-text-muted);
  text-align: left;
  cursor: pointer;
}
.nt__row--unread {
  color: var(--color-text);
}
.nt__row--unread .nt__msg {
  font-weight: var(--font-weight-semibold);
}
.nt__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 8px;
}
.nt__row--unread .nt__dot {
  background: var(--color-primary);
}
.nt__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nt__type {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}
</style>

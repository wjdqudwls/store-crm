<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ContactResultDialog from '@/components/ContactResultDialog.vue'
import { completeVisit, getFollowUps, skipFollowUp } from '@/api/customers'
import { useSessionStore } from '@/stores/session'
import { dateLabel, followUpStatusLabels } from '@/features/customer/customerDetail'

/**
 * 후속 연락 (05 SCR-06, CAL-06). `OPEN` 할 일에 [연락 결과] [건너뛰기].
 * 매장 계정은 전부, 직원은 내 할 일만 (R1). 본사는 보기만.
 */
const props = defineProps({
  customerId: { type: Number, required: true },
  marketingAllowed: { type: Boolean, default: true },
})
const session = useSessionStore()

const list = ref(null)
async function load() {
  list.value = (await getFollowUps(props.customerId)).content
}
onMounted(load)

function canAct(t) {
  if (t.status !== 'OPEN' || session.isHq) return false
  return session.isStore || t.staffId === session.me.id
}

async function skip(t) {
  try {
    await ElMessageBox.confirm(`'${t.stage}' 할 일을 건너뛸까요?`, '건너뛰기', {
      confirmButtonText: '건너뛰기',
      cancelButtonText: '돌아가기',
    })
  } catch {
    return
  }
  try {
    await skipFollowUp(t.id)
    ElMessage.success('건너뛰었습니다')
  } catch (e) {
    ElMessage.warning(
      e?.status === 409 ? '이미 처리된 할 일입니다' : (e?.body?.message ?? '다시 눌러 주세요'),
    )
  }
  load()
}

async function visited(t) {
  try {
    await completeVisit(t.id)
    ElMessage.success('방문을 확인했습니다')
  } catch (e) {
    ElMessage.warning(e?.body?.message ?? '다시 눌러 주세요')
  }
  load()
}

const dialogOpen = ref(false)
const dialogTask = ref(null)
function openResult(t) {
  dialogTask.value = t
  dialogOpen.value = true
}

const statusType = {
  OPEN: 'primary',
  DONE: 'success',
  SKIPPED: 'info',
  CANCELLED: 'info',
  REPLACED: 'info',
}
</script>

<template>
  <div class="ftab">
    <el-skeleton v-if="!list" animated :rows="3" />
    <p v-else-if="!list.length" class="empty">후속 연락 할 일이 없습니다.</p>
    <ul v-else class="flist">
      <li
        v-for="t in list"
        :key="t.id"
        class="task"
        :class="{ 'task--closed': t.status !== 'OPEN' }"
      >
        <div class="task__main">
          <div class="task__title">
            <el-tag size="small" :type="statusType[t.status]">
              {{ followUpStatusLabels[t.status] }}
            </el-tag>
            <strong>{{ t.stage }}</strong>
            <span v-if="t.promotional && !marketingAllowed" class="task__nopromo">
              홍보 연락 불가 · 안부 연락만
            </span>
          </div>
          <div class="task__meta">
            <template v-if="t.soldAt">기준 구매 {{ dateLabel(t.soldAt) }} · </template>
            <template v-else-if="t.noteContent">{{ t.noteContent }} · </template>
            {{ t.kind === 'VISIT' ? '방문일' : '만기' }} {{ t.dueDate }} · 받는 사람
            {{ t.assignee === 'STORE' ? '매장' : t.staffName }}
            <template v-if="t.closedAt"> · 처리 {{ dateLabel(t.closedAt) }}</template>
          </div>
        </div>
        <div v-if="canAct(t)" class="task__btns">
          <el-button v-if="t.kind === 'VISIT'" size="small" type="primary" @click="visited(t)"
            >방문함</el-button
          >
          <el-button v-else size="small" type="primary" @click="openResult(t)">연락 결과</el-button>
          <el-button size="small" @click="skip(t)">건너뛰기</el-button>
        </div>
      </li>
    </ul>

    <ContactResultDialog v-model="dialogOpen" :task="dialogTask" @saved="load" />
  </div>
</template>

<style scoped>
.flist {
  list-style: none;
  margin: 0;
  padding: 0;
}
.task {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}
.task--closed {
  opacity: 0.75;
}
.task__main {
  flex: 1 1 260px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.task__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.task__nopromo {
  color: var(--color-warning);
  font-size: var(--font-size-sm);
}
.task__meta {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.task__btns {
  display: flex;
  gap: 4px;
}
</style>

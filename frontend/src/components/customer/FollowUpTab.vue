<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import ChoiceButtons from '@/components/ChoiceButtons.vue'
import { completeFollowUp, getFollowUps, skipFollowUp } from '@/api/customers'
import { useSessionStore } from '@/stores/session'
import {
  callResultLabels,
  channelLabels,
  dateLabel,
  followUpStatusLabels,
} from '@/features/customer/customerDetail'

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

function onError(e) {
  if (e?.status === 409) {
    ElMessage.warning('이미 처리된 할 일입니다')
    load()
  } else {
    ElMessage.error(e?.body?.message ?? '처리됐는지 확인하지 못했습니다. 다시 눌러 주세요')
  }
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
    load()
  } catch (e) {
    onError(e)
  }
}

// ---- 연락 결과 창 (SCR-07과 같은 창) ----
const resultOptions = Object.entries(callResultLabels).map(([value, label]) => ({ value, label }))
const channelOptions = Object.entries(channelLabels).map(([value, label]) => ({ value, label }))
const dialogTask = ref(null)
const form = reactive({ staffId: null, callResult: null, channel: 'CALL', content: '' })
const errors = ref({})
const saving = ref(false)

function openResult(t) {
  Object.assign(form, { staffId: null, callResult: null, channel: 'CALL', content: '' })
  errors.value = {}
  dialogTask.value = t
}

const dialogOpen = computed({
  get: () => dialogTask.value != null,
  set: (v) => {
    if (!v) dialogTask.value = null
  },
})

async function saveResult() {
  const e = {}
  if (session.isStore && !form.staffId) e.staffId = '연락한 직원을 골라 주세요'
  if (!form.callResult) e.callResult = '연락 결과를 골라 주세요'
  if (!form.content.trim()) e.content = '내용을 입력해 주세요'
  errors.value = e
  if (Object.keys(e).length) return
  saving.value = true
  try {
    const body = {
      callResult: form.callResult,
      channel: form.channel,
      content: form.content.trim(),
    }
    if (session.isStore) body.staffId = form.staffId
    await completeFollowUp(dialogTask.value.id, body)
    dialogTask.value = null
    ElMessage.success('연락 결과를 남기고 할 일을 완료했습니다')
    load()
  } catch (err) {
    onError(err)
  } finally {
    saving.value = false
  }
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
    <p v-else-if="!list.length" class="ftab__empty">후속 연락 할 일이 없습니다.</p>
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
            기준 구매 {{ dateLabel(t.soldAt) }} · 만기 {{ t.dueDate }} · 받는 사람
            {{ t.assignee === 'STORE' ? '매장' : t.staffName }}
            <template v-if="t.closedAt"> · 처리 {{ dateLabel(t.closedAt) }}</template>
          </div>
        </div>
        <div v-if="canAct(t)" class="task__btns">
          <el-button size="small" type="primary" @click="openResult(t)">연락 결과</el-button>
          <el-button size="small" @click="skip(t)">건너뛰기</el-button>
        </div>
      </li>
    </ul>

    <el-dialog v-model="dialogOpen" title="연락 결과" width="min(480px, 94vw)" align-center>
      <div v-if="dialogTask" class="rform">
        <p class="rform__task">{{ dialogTask.stage }} · 만기 {{ dialogTask.dueDate }}</p>
        <div v-if="session.isStore">
          <StaffSelect v-model="form.staffId" placeholder="연락한 직원" />
          <p v-if="errors.staffId" class="rform__error">{{ errors.staffId }}</p>
        </div>
        <p v-else class="rform__me">연락한 직원: {{ session.me.name }}</p>
        <div>
          <ChoiceButtons v-model="form.callResult" :options="resultOptions" />
          <p v-if="errors.callResult" class="rform__error">{{ errors.callResult }}</p>
        </div>
        <ChoiceButtons v-model="form.channel" :options="channelOptions" size="small" />
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="통화 내용"
        />
        <p v-if="errors.content" class="rform__error">{{ errors.content }}</p>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">돌아가기</el-button>
        <el-button type="primary" :loading="saving" @click="saveResult">저장</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ftab__empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-muted);
}
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
  font-variant-numeric: tabular-nums;
}
.task__btns {
  display: flex;
  gap: 4px;
}
.rform {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rform__task {
  margin: 0;
  font-weight: var(--font-weight-semibold);
}
.rform__me {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.rform__error {
  margin: 4px 0 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
</style>

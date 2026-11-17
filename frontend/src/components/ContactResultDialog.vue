<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import ChoiceButtons from '@/components/ChoiceButtons.vue'
import ReminderFields from '@/components/ReminderFields.vue'
import { completeFollowUp, createNote } from '@/api/customers'
import { useSessionStore } from '@/stores/session'
import { useIsPhone } from '@/composables/useIsPhone'
import { callResultLabels, channelLabels } from '@/features/customer/customerDetail'

/**
 * 연락 결과 창 (05 SCR-07 W4). 할 일이 있으면 완료(`POST /follow-ups/{id}/complete`),
 * 콜리스트처럼 할 일이 없으면 연락 메모(`POST /customers/{id}/notes`, type CALL, W7)로 남긴다.
 * 매장 계정은 연락한 직원을 고르고, 직원은 본인으로 남는다 (R1). 폰에서는 아래에서 올라온다.
 */
const open = defineModel({ type: Boolean, default: false })
const props = defineProps({
  /** 할 일 (id, stage, dueDate, customerName) */
  task: { type: Object, default: null },
  /** 할 일 없이 남길 때 고객 (id, name) */
  customer: { type: Object, default: null },
})
const emit = defineEmits(['saved'])
const session = useSessionStore()
const isPhone = useIsPhone()

const resultOptions = Object.entries(callResultLabels).map(([value, label]) => ({ value, label }))
const channelOptions = Object.entries(channelLabels).map(([value, label]) => ({ value, label }))
const blank = () => ({
  staffId: null,
  callResult: null,
  channel: 'CALL',
  content: '',
  reminderOn: false,
  reminderDate: null,
  reminderStaffId: null,
})
const form = reactive(blank())
const errors = ref({})
const saving = ref(false)

watch(open, (v) => {
  if (v) {
    Object.assign(form, blank())
    errors.value = {}
  }
})

const title = computed(() => {
  const name = props.task?.customerName ?? props.customer?.name
  return name ? `연락 결과 · ${name}` : '연락 결과'
})

async function save() {
  const e = {}
  if (session.isStore && !form.staffId) e.staffId = '연락한 직원을 골라 주세요'
  if (!form.callResult) e.callResult = '연락 결과를 골라 주세요'
  // 할 일 완료는 내용이 선택, 할 일 없는 연락 메모는 내용이 필요하다
  if (!props.task && !form.content.trim()) e.content = '내용을 입력해 주세요'
  if (form.callResult === 'RECALL' && form.reminderOn && !form.reminderDate)
    e.reminder = '다시 연락할 날을 골라 주세요'
  errors.value = e
  if (Object.keys(e).length) return
  saving.value = true
  try {
    const body = {
      callResult: form.callResult,
      channel: form.channel,
      content: form.content.trim() || callResultLabels[form.callResult],
    }
    if (session.isStore) body.staffId = form.staffId
    if (form.callResult === 'RECALL' && form.reminderOn) {
      body.reminder = { date: form.reminderDate, staffId: form.reminderStaffId ?? undefined }
    }
    if (props.task) await completeFollowUp(props.task.id, body)
    else await createNote(props.customer.id, { ...body, type: 'CALL' })
    open.value = false
    ElMessage.success('연락 결과를 남겼습니다')
    emit('saved')
  } catch (err) {
    if (err?.status === 409) {
      open.value = false
      ElMessage.warning('이미 처리된 할 일입니다')
      emit('saved')
    } else {
      ElMessage.error(err?.body?.message ?? '저장됐는지 확인하지 못했습니다. 다시 눌러 주세요')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    v-if="isPhone"
    v-model="open"
    direction="btt"
    size="auto"
    :title="title"
    class="result-drawer"
  >
    <div class="rform">
      <p v-if="task" class="rform__task">{{ task.stage }} · 만기 {{ task.dueDate }}</p>
      <div v-if="session.isStore">
        <StaffSelect v-model="form.staffId" placeholder="연락한 직원" />
        <p v-if="errors.staffId" class="field-error">{{ errors.staffId }}</p>
      </div>
      <p v-else class="muted">연락한 직원: {{ session.me.name }}</p>
      <div>
        <ChoiceButtons v-model="form.callResult" :options="resultOptions" />
        <p v-if="errors.callResult" class="field-error">{{ errors.callResult }}</p>
      </div>
      <ChoiceButtons v-model="form.channel" :options="channelOptions" size="small" />
      <ReminderFields
        v-if="form.callResult === 'RECALL'"
        v-model:enabled="form.reminderOn"
        v-model:date="form.reminderDate"
        v-model:staff-id="form.reminderStaffId"
        toggle-label="다시 연락할 날 정하고 알림 받기"
        date-placeholder="다시 연락할 날"
        :writer-name="session.isStore ? '' : session.me.name"
        :error="errors.reminder"
      />
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        :placeholder="task ? '통화 내용 (선택)' : '통화 내용'"
      />
      <p v-if="errors.content" class="field-error">{{ errors.content }}</p>
      <el-button type="primary" size="large" :loading="saving" @click="save">저장</el-button>
    </div>
  </el-drawer>

  <el-dialog v-else v-model="open" :title="title" width="min(480px, 94vw)" align-center>
    <div class="rform">
      <p v-if="task" class="rform__task">{{ task.stage }} · 만기 {{ task.dueDate }}</p>
      <div v-if="session.isStore">
        <StaffSelect v-model="form.staffId" placeholder="연락한 직원" />
        <p v-if="errors.staffId" class="field-error">{{ errors.staffId }}</p>
      </div>
      <p v-else class="muted">연락한 직원: {{ session.me.name }}</p>
      <div>
        <ChoiceButtons v-model="form.callResult" :options="resultOptions" />
        <p v-if="errors.callResult" class="field-error">{{ errors.callResult }}</p>
      </div>
      <ChoiceButtons v-model="form.channel" :options="channelOptions" size="small" />
      <ReminderFields
        v-if="form.callResult === 'RECALL'"
        v-model:enabled="form.reminderOn"
        v-model:date="form.reminderDate"
        v-model:staff-id="form.reminderStaffId"
        toggle-label="다시 연락할 날 정하고 알림 받기"
        date-placeholder="다시 연락할 날"
        :writer-name="session.isStore ? '' : session.me.name"
        :error="errors.reminder"
      />
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        :placeholder="task ? '통화 내용 (선택)' : '통화 내용'"
      />
      <p v-if="errors.content" class="field-error">{{ errors.content }}</p>
    </div>
    <template #footer>
      <el-button @click="open = false">돌아가기</el-button>
      <el-button type="primary" :loading="saving" @click="save">저장</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rform {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rform__task {
  margin: 0;
  font-weight: var(--font-weight-semibold);
}
</style>

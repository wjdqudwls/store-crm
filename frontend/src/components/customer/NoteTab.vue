<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import { ElMessage, ElMessageBox } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import ChoiceButtons from '@/components/ChoiceButtons.vue'
import ReminderFields from '@/components/ReminderFields.vue'
import { createNote, deleteNote, getNotes, updateNote } from '@/api/customers'
import { getStaff } from '@/api/sales'
import { useSessionStore } from '@/stores/session'
import {
  callResultLabels,
  channelLabels,
  dateTimeLabel,
  noteTypeLabels,
} from '@/features/customer/customerDetail'

/**
 * 메모 (05 SCR-06, MEM-01~03, CAL-02). 위에 작성 칸, 아래 최신순 목록.
 * 매장 계정은 작성 직원을 고르고, 직원 개인은 본인으로 남는다 (R1). 본사는 읽고 고치기만.
 */
const props = defineProps({ customerId: { type: Number, required: true } })
const session = useSessionStore()
const canWrite = computed(() => !session.isHq)

const list = ref(null)
const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    list.value = (await getNotes(props.customerId)).content
  })
onMounted(load)

const typeOptions = [
  { value: 'CONSULT', label: '상담' },
  { value: 'CALL', label: '연락' },
  { value: 'RESERVATION', label: '예약' },
  { value: 'ETC', label: '기타' },
]
const resultOptions = Object.entries(callResultLabels).map(([value, label]) => ({ value, label }))
const channelOptions = Object.entries(channelLabels).map(([value, label]) => ({ value, label }))

const blank = () => ({
  type: 'CONSULT',
  content: '',
  staffId: null,
  callResult: null,
  channel: null,
  reminderOn: false,
  reminderDate: null,
  reminderStaffId: null,
})
const draft = reactive(blank())
const errors = ref({})
const saving = ref(false)

function validate() {
  const e = {}
  if (session.isStore && !draft.staffId) e.staffId = '작성 직원을 골라 주세요'
  if (!draft.content.trim()) e.content = '내용을 입력해 주세요'
  if (draft.type === 'CALL') {
    if (!draft.callResult) e.callResult = '연락 결과를 골라 주세요'
    if (!draft.channel) e.channel = '채널을 골라 주세요'
    if (draft.callResult === 'RECALL' && draft.reminderOn && !draft.reminderDate)
      e.reminder = '다시 연락할 날을 골라 주세요'
  }
  if (draft.type === 'RESERVATION' && !draft.reminderDate) e.reminder = '방문 날짜를 골라 주세요'
  return e
}

async function save() {
  errors.value = validate()
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    const body = { type: draft.type, content: draft.content.trim() }
    if (session.isStore) body.staffId = draft.staffId
    if (draft.type === 'CALL')
      Object.assign(body, { callResult: draft.callResult, channel: draft.channel })
    const wantsReminder =
      draft.type === 'RESERVATION' ||
      (draft.type === 'CALL' && draft.callResult === 'RECALL' && draft.reminderOn)
    if (wantsReminder) {
      body.reminder = { date: draft.reminderDate, staffId: draft.reminderStaffId ?? undefined }
    }
    await createNote(props.customerId, body)
    Object.assign(draft, blank())
    ElMessage.success(
      body.reminder
        ? `메모를 남겼습니다. ${body.reminder.date}에 맞춰 알림이 갑니다`
        : '메모를 남겼습니다',
    )
    await load()
  } catch (e) {
    ElMessage.error(e?.body?.message ?? '저장됐는지 확인하지 못했습니다. 다시 눌러 주세요')
  } finally {
    saving.value = false
  }
}

const staffOf = (id) => staffList.value.find((x) => x.id === id)?.name
const writerName = computed(() =>
  session.isStore ? (staffOf(draft.staffId) ?? '') : session.me.name,
)
const staffList = ref([])
onMounted(async () => {
  staffList.value = await getStaff().catch(() => []) // 직원 목록이 없어도 메모 목록은 보인다
})
function shortDate(ymd) {
  const [, m, d] = ymd.split('-').map(Number)
  return `${m}/${d}`
}

// ---- 수정·삭제 (editable인 메모만, MEM-03) ----
const editingId = ref(null)
const editText = ref('')
function startEdit(n) {
  editingId.value = n.id
  editText.value = n.content
}
async function saveEdit(n) {
  if (!editText.value.trim()) return
  await updateNote(n.id, editText.value.trim())
  editingId.value = null
  await load()
}
async function remove(n) {
  try {
    await ElMessageBox.confirm('이 메모를 지울까요? 지우면 되돌릴 수 없습니다.', '메모 삭제', {
      confirmButtonText: '삭제',
      cancelButtonText: '돌아가기',
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteNote(n.id)
  ElMessage.success('메모를 지웠습니다')
  await load()
}

const typeTag = {
  RESERVATION: 'danger',
  CONSULT: 'primary',
  PURCHASE: 'success',
  CALL: 'warning',
  ETC: 'info',
}
</script>

<template>
  <div class="ntab">
    <form v-if="canWrite" class="nform" @submit.prevent="save">
      <div class="ftable">
        <div class="ft-caption">메모 쓰기</div>
        <div class="ft-label is-required">유형</div>
        <div class="ft-value">
          <ChoiceButtons v-model="draft.type" :options="typeOptions" size="small" />
        </div>
        <div class="ft-label" :class="{ 'is-required': session.isStore }">작성 직원</div>
        <div class="ft-value">
          <template v-if="session.isStore">
            <StaffSelect v-model="draft.staffId" placeholder="작성 직원" />
            <p v-if="errors.staffId" class="field-error">{{ errors.staffId }}</p>
          </template>
          <span v-else>{{ session.me.name }}</span>
        </div>
        <template v-if="draft.type === 'CALL'">
          <div class="ft-label is-required">연락 결과</div>
          <div class="ft-value">
            <ChoiceButtons v-model="draft.callResult" :options="resultOptions" size="small" />
            <p v-if="errors.callResult" class="field-error">{{ errors.callResult }}</p>
          </div>
          <div class="ft-label is-required">채널</div>
          <div class="ft-value">
            <ChoiceButtons v-model="draft.channel" :options="channelOptions" size="small" />
            <p v-if="errors.channel" class="field-error">{{ errors.channel }}</p>
          </div>
          <template v-if="draft.callResult === 'RECALL'">
            <div class="ft-label">다시 연락할 날</div>
            <div class="ft-value ft-wide">
              <ReminderFields
                v-model:enabled="draft.reminderOn"
                v-model:date="draft.reminderDate"
                v-model:staff-id="draft.reminderStaffId"
                toggle-label="날짜 정하고 알림 받기"
                date-placeholder="다시 연락할 날"
                :writer-name="writerName"
                :error="errors.reminder"
              />
            </div>
          </template>
        </template>
        <template v-if="draft.type === 'RESERVATION'">
          <div class="ft-label is-required">방문 날짜</div>
          <div class="ft-value ft-wide">
            <ReminderFields
              v-model:date="draft.reminderDate"
              v-model:staff-id="draft.reminderStaffId"
              required
              date-placeholder="방문 날짜"
              :writer-name="writerName"
              :error="errors.reminder"
            />
          </div>
        </template>
        <label class="ft-label is-required" for="note-content">내용</label>
        <div class="ft-value ft-wide">
          <el-input
            id="note-content"
            v-model="draft.content"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :placeholder="
              draft.type === 'RESERVATION'
                ? '방문 이유 (예: 코트 소매 수선 맡기러 옴)'
                : '상담 내용, 고객 취향, 다음에 권할 것'
            "
          />
          <p v-if="errors.content" class="field-error">{{ errors.content }}</p>
        </div>
      </div>
      <div class="nform__actions">
        <el-button type="primary" native-type="submit" :loading="saving">
          {{ saving ? '저장 중…' : '메모 남기기' }}
        </el-button>
      </div>
    </form>

    <LoadError :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-if="!list && !error" animated :rows="3" />
    <p v-else-if="list && !list.length" class="ntab__empty">메모가 없습니다.</p>
    <ul v-else-if="list" class="nlist">
      <li v-for="n in list" :key="n.id" class="note">
        <div class="note__head">
          <el-tag size="small" :type="typeTag[n.type]" effect="light">
            {{ noteTypeLabels[n.type] }}
          </el-tag>
          <span v-if="n.callResult" class="note__call">
            {{ callResultLabels[n.callResult] }} · {{ channelLabels[n.channel] }}
          </span>
          <span v-if="n.reminderDate" class="note__reminder">
            {{ n.type === 'RESERVATION' ? '방문' : '다시 연락' }} {{ shortDate(n.reminderDate) }}
          </span>
          <span class="note__staff">{{ n.staffName }}</span>
          <span class="note__date">
            {{ dateTimeLabel(n.createdAt) }}<template v-if="n.updatedAt"> · 수정됨</template>
          </span>
          <span v-if="n.editable && editingId !== n.id" class="note__btns">
            <el-button link size="small" @click="startEdit(n)">수정</el-button>
            <el-button link size="small" type="danger" @click="remove(n)">삭제</el-button>
          </span>
        </div>
        <template v-if="editingId === n.id">
          <el-input v-model="editText" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          <div class="note__edit-actions">
            <el-button size="small" @click="editingId = null">돌아가기</el-button>
            <el-button size="small" type="primary" @click="saveEdit(n)">저장</el-button>
          </div>
        </template>
        <p v-else class="note__content">{{ n.content }}</p>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.ntab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.nform {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.nform__row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.nform__staff {
  width: 200px;
}
.nform__me {
  align-self: center;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.nform__error {
  margin: 4px 0 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
.nform__actions {
  display: flex;
  justify-content: flex-end;
}
.ntab__empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-muted);
}
.nlist {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.note {
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.note__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  font-size: var(--font-size-sm);
}
.note__reminder {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--el-color-danger-light-9);
  color: var(--color-danger);
  font-weight: var(--font-weight-semibold);
}
.note__call {
  font-weight: var(--font-weight-semibold);
}
.note__staff,
.note__date {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}
.note__btns {
  margin-left: auto;
}
.note__content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}
.note__edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

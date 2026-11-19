<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChoiceButtons from '@/components/ChoiceButtons.vue'
import { createMember, deleteMember, getMembers, updateMember } from '@/api/customers'
import { getSizes } from '@/api/public'
import { useSessionStore } from '@/stores/session'
import { showReference } from '@/config/reference'
import { genderLabels, relationLabels } from '@/features/customer/customerDetail'
import { sizeChoices, sizeItemType } from '@/features/customer/customerForm'

/**
 * 가족 구성원 (05 SCR-06, CUS-05). 이름 대신 호칭으로 받는다 (BR-13).
 * 매장 계정만 추가·수정·삭제한다. 직원·본사는 보기만.
 * 사이즈는 구성원 성별의 목록에서 고르고, 자녀는 아동 사이즈를 직접 적을 수 있다.
 */
const props = defineProps({ customerId: { type: Number, required: true } })
const session = useSessionStore()
const canEdit = computed(() => session.isStore && showReference)

const list = ref(null)
const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    list.value = await getMembers(props.customerId)
  })
const sizeTable = ref([])
onMounted(async () => {
  load()
  sizeTable.value = await getSizes().catch(() => []) // 사이즈표가 없어도 목록은 보인다
})

const sizeKeys = [
  ['top', '상의'],
  ['outer', '아우터'],
  ['bottom', '하의'],
  ['shoes', '신발'],
]

// ---- 추가·수정 창 ----
const blank = () => ({
  nickname: '',
  relation: null,
  gender: null,
  birthYear: '',
  sizes: { top: '', outer: '', bottom: '', shoes: '' },
})
const form = reactive(blank())
const editingId = ref(null)
const open = ref(false)
const errors = ref({})
const saving = ref(false)

function openNew() {
  Object.assign(form, blank())
  editingId.value = null
  errors.value = {}
  open.value = true
}
function openEdit(m) {
  Object.assign(form, {
    nickname: m.nickname,
    relation: m.relation,
    gender: m.gender ?? null,
    birthYear: m.birthYear ? String(m.birthYear) : '',
    sizes: { top: '', outer: '', bottom: '', shoes: '', ...m.sizes },
  })
  editingId.value = m.id
  errors.value = {}
  open.value = true
}

const relationOptions = Object.entries(relationLabels).map(([value, label]) => ({ value, label }))
const genderOptions = Object.entries(genderLabels).map(([value, label]) => ({ value, label }))
const optionsFor = (key) => sizeChoices(sizeTable.value, sizeItemType[key], form.gender)

async function save() {
  const e = {}
  if (!form.nickname.trim()) e.nickname = '호칭을 입력해 주세요 (예: 첫째 딸)'
  if (!form.relation) e.relation = '관계를 골라 주세요'
  const y = Number(form.birthYear)
  if (
    form.birthYear &&
    (!/^\d{4}$/.test(form.birthYear) || y < 1900 || y > new Date().getFullYear())
  )
    e.birthYear = '4자리 연도로 입력해 주세요'
  errors.value = e
  if (Object.keys(e).length) return
  const sizes = Object.fromEntries(Object.entries(form.sizes).filter(([, v]) => v))
  const body = {
    nickname: form.nickname,
    relation: form.relation,
    gender: form.gender,
    birthYear: form.birthYear ? y : null,
    sizes,
  }
  saving.value = true
  try {
    if (editingId.value) await updateMember(editingId.value, body)
    else await createMember(props.customerId, body)
    open.value = false
    ElMessage.success(editingId.value ? '구성원을 고쳤습니다' : '구성원을 추가했습니다')
    load()
  } catch (err) {
    ElMessage.error(err?.body?.message ?? '저장됐는지 확인하지 못했습니다')
  } finally {
    saving.value = false
  }
}

async function remove(m) {
  try {
    await ElMessageBox.confirm(
      `'${m.nickname}'을(를) 지울까요? 지난 판매의 착용자 표시는 그대로 남습니다.`,
      '구성원 삭제',
      { confirmButtonText: '삭제', cancelButtonText: '돌아가기', type: 'warning' },
    )
  } catch {
    return
  }
  await deleteMember(m.id)
  ElMessage.success('구성원을 지웠습니다')
  load()
}
</script>

<template>
  <div class="fam-tab">
    <div v-if="canEdit" class="fam__bar">
      <el-button size="small" type="primary" plain @click="openNew">구성원 추가</el-button>
    </div>
    <LoadError :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-if="!list && !error" animated :rows="2" />
    <p v-else-if="list && !list.length" class="empty">등록한 가족 구성원이 없습니다.</p>
    <ul v-else-if="list" class="fam">
      <li v-for="m in list" :key="m.id" class="member">
        <div class="member__head">
          <strong>{{ m.nickname }}</strong>
          <span class="member__meta">
            {{ relationLabels[m.relation] }}
            <template v-if="m.gender"> · {{ genderLabels[m.gender] }}</template>
            <template v-if="m.birthYear"> · {{ m.birthYear }}년생</template>
          </span>
          <span v-if="canEdit" class="member__btns">
            <el-button link size="small" @click="openEdit(m)">수정</el-button>
            <el-button link size="small" type="danger" @click="remove(m)">삭제</el-button>
          </span>
        </div>
        <dl class="member__sizes">
          <template v-for="[k, label] in sizeKeys" :key="k">
            <template v-if="m.sizes?.[k]">
              <dt>{{ label }}</dt>
              <dd>{{ m.sizes[k] }}</dd>
            </template>
          </template>
        </dl>
      </li>
    </ul>

    <el-dialog
      v-model="open"
      :title="editingId ? '구성원 수정' : '구성원 추가'"
      width="min(640px, 94vw)"
      align-center
    >
      <div class="ftable">
        <label class="ft-label is-required" for="mem-nick">호칭</label>
        <div class="ft-value">
          <el-input id="mem-nick" v-model="form.nickname" maxlength="20" placeholder="첫째 딸" />
          <p v-if="errors.nickname" class="field-error">{{ errors.nickname }}</p>
        </div>
        <div class="ft-label is-required">관계</div>
        <div class="ft-value">
          <ChoiceButtons v-model="form.relation" :options="relationOptions" size="small" />
          <p v-if="errors.relation" class="field-error">{{ errors.relation }}</p>
        </div>
        <div class="ft-label">성별</div>
        <div class="ft-value">
          <ChoiceButtons v-model="form.gender" :options="genderOptions" size="small" />
        </div>
        <label class="ft-label" for="mem-year">출생연도</label>
        <div class="ft-value">
          <el-input id="mem-year" v-model="form.birthYear" maxlength="4" placeholder="2019" />
          <p v-if="errors.birthYear" class="field-error">{{ errors.birthYear }}</p>
        </div>
        <div class="ft-label">사이즈</div>
        <div class="ft-value ft-wide">
          <div class="mem__sizes">
            <label v-for="[k, label] in sizeKeys" :key="k" class="mem__size">
              <span class="muted">{{ label }}</span>
              <el-select
                v-model="form.sizes[k]"
                filterable
                allow-create
                clearable
                :placeholder="form.relation === 'CHILD' ? '직접 입력' : '선택'"
              >
                <el-option v-for="s in optionsFor(k)" :key="s" :label="s" :value="s" />
              </el-select>
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="open = false">돌아가기</el-button>
        <el-button type="primary" :loading="saving" @click="save">저장</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.fam__bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.fam {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.member {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.member__head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
}
.member__meta {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.member__btns {
  margin-left: auto;
}
.member__sizes {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 12px;
  margin: 0;
  font-size: var(--font-size-sm);
}
.member__sizes dt {
  color: var(--color-text-muted);
}
.member__sizes dd {
  margin: 0;
}
.mem__sizes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}
.mem__size {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mem__size .muted {
  flex: 0 0 48px;
}
</style>

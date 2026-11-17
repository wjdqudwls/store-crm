<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import ChoiceButtons from '@/components/ChoiceButtons.vue'
import { getSizes } from '@/api/public'
import { createCustomer } from '@/api/customers'
import { useSessionStore } from '@/stores/session'
import { itemTypeLabels, sizeLabel } from '@/utils/format'
import {
  emptyForm,
  formatDomesticPhone,
  sizeChoices,
  sizeItemType,
  visibleSizeFields,
  validateRegistration,
  toRegistrationBody,
  toProfilePatch,
  daysInMonth,
  sidoList,
} from '@/features/customer/customerForm'

/**
 * 고객 등록 폼 (05 SCR-04). 고객 메뉴의 화면과 판매 기록 안의 창이 같이 쓴다.
 * 키오스크·QR(F5)도 이 폼을 쓰고 담당 직원 칸을 빼고 선택 항목을 펼친다 (C3).
 */
const props = defineProps({
  /** 판매 기록에서 열면 판매 사원 (C2) */
  presetStaffId: { type: Number, default: null },
  /** 고객 찾기에서 넘어온 이름 */
  presetName: { type: String, default: '' },
  /** 판매 중이면 중복일 때 버튼이 [이 고객으로 판매] */
  inSale: { type: Boolean, default: false },
  showStaff: { type: Boolean, default: true },
  collapseOptional: { type: Boolean, default: true },
  /** 키오스크·QR은 다른 API로 보낸다 (기본: POST /customers) */
  submitFn: { type: Function, default: null },
  /** 키오스크·QR: 필수 동의 2개 전에는 [등록]을 끈다 (SCR-01, U6) */
  lockUntilConsent: { type: Boolean, default: false },
  /** 가입매장 이름 (QR은 로그인 계정이 없다) */
  storeName: { type: String, default: '' },
  /** 화면 위에 [등록]을 따로 둘 때 폼 아래 버튼을 숨긴다 (PC 고객 등록 화면) */
  hideActions: { type: Boolean, default: false },
  /** 'edit': 프로필 수정 (CUS-04). 필수 동의 칸을 숨기고 `toProfilePatch`로 보낸다 */
  mode: { type: String, default: 'create' },
  /** 수정할 때 처음 값 (`formFromCustomer`) */
  initial: { type: Object, default: null },
})
const emit = defineEmits(['created', 'saved', 'use-existing'])
const editing = computed(() => props.mode === 'edit')

const session = useSessionStore()
const base = () =>
  props.initial
    ? JSON.parse(JSON.stringify(props.initial))
    : { ...emptyForm(), staffId: props.presetStaffId, name: props.presetName }
const form = reactive(base())
const errors = ref({})
const duplicate = ref(null) // 409 details
const saving = ref(false)
const showOptional = ref(!props.collapseOptional)
const showConsentText = ref(false)
const sizeTable = ref([])

onMounted(async () => {
  sizeTable.value = await getSizes()
})

const foreign = computed(() => form.nationality === 'FOREIGN')

function onPhoneInput(v) {
  form.phone = foreign.value ? v.replace(/[^\d+\s-]/g, '') : formatDomesticPhone(v)
}

watch(
  () => form.nationality,
  () => {
    form.phone = ''
    errors.value = {}
  },
)

// 성별이 바뀌면 아우터·하의 목록이 달라지므로 비운다
watch(
  () => form.gender,
  () => {
    form.sizes.outer = ''
    form.sizes.bottom = ''
  },
)

const sizeFieldLabels = { top: '상의', outer: '아우터', bottom: '하의', shoes: '신발' }
const sizeTarget = { F: 'WOMEN', M: 'MEN' }
const sizeFields = computed(() =>
  visibleSizeFields(form.gender).map((key) => {
    const itemType = sizeItemType[key]
    return {
      key,
      label: sizeFieldLabels[key],
      options: sizeChoices(sizeTable.value, itemType, form.gender).map((s) => ({
        value: s,
        label: sizeLabel(s, itemType, sizeTarget[form.gender]),
      })),
    }
  }),
)

const genderOptions = [
  { value: 'F', label: '여성' },
  { value: 'M', label: '남성' },
]
const channelOptions = [
  { value: 'CALL', label: '전화' },
  { value: 'SMS', label: '문자' },
  { value: 'KAKAO', label: '카톡' },
]
const itemTypeOptions = Object.entries(itemTypeLabels).map(([value, label]) => ({ value, label }))
const months = Array.from({ length: 12 }, (_, i) => i + 1)
const days = computed(() => Array.from({ length: daysInMonth(form.birthMonth) }, (_, i) => i + 1))
watch(
  () => form.birthMonth,
  () => {
    if (form.birthDay > daysInMonth(form.birthMonth)) form.birthDay = null
  },
)

const consentsOk = computed(() => form.privacy && form.age14)

const optionalFields = new Set(['birthYear', 'birthMd'])
async function focusFirstError() {
  const first = Object.keys(errors.value)[0]
  if (!first) return
  if (optionalFields.has(first)) showOptional.value = true
  await nextTick()
  const el = rootRef.value?.querySelector(`[data-field="${first}"]`)
  el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  el?.querySelector('input')?.focus()
}

const rootRef = ref()

async function submit() {
  duplicate.value = null
  errors.value = validateRegistration(form)
  if (Object.keys(errors.value).length) {
    focusFirstError()
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      const res = await props.submitFn(toProfilePatch(form))
      emit('saved', res)
    } else {
      const res = await (props.submitFn ?? createCustomer)(toRegistrationBody(form))
      emit('created', res)
    }
  } catch (e) {
    const body = e?.body
    if (e?.status === 409 && body?.code === 'CUSTOMER_PHONE_DUPLICATED') {
      duplicate.value = body.details
    } else if (e?.status === 400 && body?.fieldErrors?.length) {
      errors.value = Object.fromEntries(body.fieldErrors.map((f) => [f.field, f.reason]))
      focusFirstError()
    } else {
      ElMessage.error(body?.message ?? '저장됐는지 확인하지 못했습니다. 다시 눌러 주세요')
    }
  } finally {
    saving.value = false
  }
}

/** 입력한 것이 있는지 (창을 닫을 때 버릴지 묻는다) */
function isDirty() {
  return JSON.stringify(form) !== JSON.stringify(base())
}

defineExpose({ isDirty, submit, saving, consentsOk })
</script>

<template>
  <form ref="rootRef" class="cform" novalidate @submit.prevent="submit">
    <!-- 409 중복: 기존 정보는 바꾸지 않는다 (REG-05) -->
    <div v-if="duplicate" class="cform__dup" role="alert">
      <span>
        {{ editing ? '이미 다른 고객의 번호입니다' : '이미 등록된 고객입니다' }} ·
        <strong>{{ duplicate.name }}</strong>
        {{ duplicate.phoneMasked }}
      </span>
      <el-button type="primary" size="small" @click="emit('use-existing', duplicate.customerId)">
        {{ inSale ? '이 고객으로 판매' : editing ? '그 고객 보기' : '기존 고객 보기' }}
      </el-button>
    </div>

    <p class="ft-legend"><b>빨간 글씨</b>는 필수 칸입니다.</p>

    <!-- 기본 · 동의 -->
    <div class="ftable">
      <div class="ft-caption">기본</div>

      <div class="ft-label is-required">내/외국인</div>
      <div class="ft-value">
        <el-radio-group v-model="form.nationality">
          <el-radio-button value="DOMESTIC">내국인</el-radio-button>
          <el-radio-button value="FOREIGN">외국인</el-radio-button>
        </el-radio-group>
      </div>
      <div class="ft-label">가입매장</div>
      <div class="ft-value ft-muted">
        {{ storeName || session.me.storeName || session.me.name }}
      </div>

      <template v-if="foreign">
        <label class="ft-label is-required" for="cf-name-en">영문 이름</label>
        <div class="ft-value" data-field="nameEn">
          <el-input id="cf-name-en" v-model="form.nameEn" maxlength="100" placeholder="Aiko Sato" />
          <p v-if="errors.nameEn" class="cfield__error">{{ errors.nameEn }}</p>
        </div>
        <label class="ft-label" for="cf-name">한글 이름</label>
        <div class="ft-value" data-field="name">
          <el-input
            id="cf-name"
            v-model="form.name"
            maxlength="50"
            placeholder="비우면 영문 이름을 씁니다"
          />
          <p v-if="errors.name" class="cfield__error">{{ errors.name }}</p>
        </div>
        <label class="ft-label is-required" for="cf-phone">전화번호</label>
        <div class="ft-value ft-wide" data-field="phone">
          <el-input
            id="cf-phone"
            :model-value="form.phone"
            inputmode="tel"
            placeholder="+81 90 1234 5678"
            class="cform__phone"
            @update:model-value="onPhoneInput"
          />
          <p v-if="errors.phone" class="cfield__error">{{ errors.phone }}</p>
        </div>
      </template>
      <template v-else>
        <label class="ft-label is-required" for="cf-name">고객명</label>
        <div class="ft-value" data-field="name">
          <el-input id="cf-name" v-model="form.name" maxlength="50" placeholder="김하늘" />
          <p v-if="errors.name" class="cfield__error">{{ errors.name }}</p>
        </div>
        <label class="ft-label is-required" for="cf-phone">휴대폰 번호</label>
        <div class="ft-value" data-field="phone">
          <el-input
            id="cf-phone"
            :model-value="form.phone"
            inputmode="numeric"
            placeholder="010-0000-0000"
            @update:model-value="onPhoneInput"
          />
          <p v-if="errors.phone" class="cfield__error">{{ errors.phone }}</p>
        </div>
      </template>

      <div class="ft-caption">{{ editing ? '수신 동의' : '동의' }}</div>

      <template v-if="!editing">
        <div class="ft-label is-required">개인정보 수집·이용</div>
        <div class="ft-value ft-wide" data-field="privacy">
          <div class="ft-inline">
            <el-checkbox v-model="form.privacy">동의합니다</el-checkbox>
            <el-button link type="primary" size="small" @click="showConsentText = !showConsentText">
              {{ showConsentText ? '접기' : '내용 보기' }}
            </el-button>
          </div>
          <div v-if="showConsentText" class="cform__consent-text">
            <p><b>수집 목적</b> 매장 단골 관리, 구매이력·등급 할인 안내, 사이즈 맞춤 상담, 연락</p>
            <p>
              <b>수집 항목</b> 필수: 이름, 전화번호, 내/외국인 · 선택: 생년·생일, 주소, 성별, 선호
              품목, 사이즈, 연락 채널, 가족 구성원(호칭·관계·사이즈)
            </p>
            <p><b>상세주소</b> 사은품·우편 발송에만 쓰며 암호화해 보관합니다</p>
            <p>동의하지 않을 수 있으며, 이 경우 고객 등록과 등급 할인을 받을 수 없습니다</p>
          </div>
          <p v-if="errors.privacy" class="cfield__error">{{ errors.privacy }}</p>
        </div>

        <div class="ft-label is-required">나이 확인</div>
        <div class="ft-value" data-field="age14">
          <el-checkbox v-model="form.age14">만 14세 이상입니다</el-checkbox>
          <p v-if="errors.age14" class="cfield__error">{{ errors.age14 }}</p>
        </div>
      </template>
      <div class="ft-label">마케팅 수신</div>
      <div class="ft-value" :class="{ 'ft-wide': editing }">
        <div class="ft-inline">
          <el-checkbox v-model="form.marketingSms">문자</el-checkbox>
          <el-checkbox v-model="form.marketingKakao">카톡</el-checkbox>
        </div>
      </div>

      <!-- 직원 화면만 -->
      <template v-if="showStaff">
        <label class="ft-label" for="cf-staff">담당 직원</label>
        <div class="ft-value ft-wide" data-field="staffId">
          <StaffSelect
            id="cf-staff"
            v-model="form.staffId"
            placeholder="담당 직원을 고르세요"
            class="cform__staff"
          />
          <p v-if="errors.staffId" class="cfield__error">{{ errors.staffId }}</p>
        </div>
      </template>
    </div>

    <!-- 선택 항목 (직원 화면은 접어 둠, C3) -->
    <el-button
      v-if="collapseOptional"
      class="cform__more"
      text
      type="primary"
      @click="showOptional = !showOptional"
    >
      {{ showOptional ? '선택 항목 접기 ▲' : '더 입력하기 ▼ (생일·주소·성별·사이즈)' }}
    </el-button>

    <div v-show="showOptional" class="ftable">
      <div class="ft-caption">선택</div>

      <label class="ft-label" for="cf-year">생년</label>
      <div class="ft-value" data-field="birthYear">
        <el-input
          id="cf-year"
          v-model="form.birthYear"
          inputmode="numeric"
          maxlength="4"
          placeholder="1991"
          class="cform__year"
        />
        <p v-if="errors.birthYear" class="cfield__error">{{ errors.birthYear }}</p>
      </div>
      <div class="ft-label">생일</div>
      <div class="ft-value" data-field="birthMd">
        <div class="ft-inline">
          <el-select v-model="form.birthMonth" placeholder="월" clearable class="cform__md">
            <el-option v-for="m in months" :key="m" :label="`${m}월`" :value="m" />
          </el-select>
          <el-select v-model="form.birthDay" placeholder="일" clearable class="cform__md">
            <el-option v-for="d in days" :key="d" :label="`${d}일`" :value="d" />
          </el-select>
        </div>
        <p v-if="errors.birthMd" class="cfield__error">{{ errors.birthMd }}</p>
      </div>

      <div class="ft-label">주소</div>
      <div class="ft-value ft-wide">
        <div class="cform__address">
          <el-select v-model="form.sido" placeholder="시/도" clearable class="cform__sido">
            <el-option v-for="s in sidoList" :key="s" :label="s" :value="s" />
          </el-select>
          <el-input
            v-model="form.gu"
            maxlength="30"
            placeholder="구 (예: 강남구)"
            class="cform__gu"
          />
          <el-input
            v-model="form.detail"
            maxlength="200"
            placeholder="상세주소 (사은품·우편 발송용)"
            class="cform__detail"
          />
        </div>
      </div>

      <div class="ft-label">성별</div>
      <div class="ft-value">
        <ChoiceButtons v-model="form.gender" :options="genderOptions" />
      </div>
      <div class="ft-label">연락 채널</div>
      <div class="ft-value">
        <ChoiceButtons v-model="form.contactChannel" :options="channelOptions" />
      </div>

      <div class="ft-label">선호 품목<small>여러 개</small></div>
      <div class="ft-value ft-wide">
        <ChoiceButtons v-model="form.preferredItemTypes" :options="itemTypeOptions" multiple />
      </div>

      <div class="ft-label">사이즈</div>
      <div class="ft-value ft-wide">
        <p v-if="!form.gender" class="cform__hint">
          성별을 고르면 아우터·하의 사이즈도 고를 수 있어요
        </p>
        <div v-for="f in sizeFields" :key="f.key" class="cform__size">
          <span class="cform__size-label">{{ f.label }}</span>
          <ChoiceButtons v-model="form.sizes[f.key]" :options="f.options" size="small" />
        </div>
      </div>
    </div>

    <div v-if="!hideActions" class="cform__actions">
      <slot name="actions" />
      <span v-if="!consentsOk && !editing" class="cform__hint">필수 동의 2개에 체크해 주세요</span>
      <el-button
        type="primary"
        size="large"
        native-type="submit"
        :loading="saving"
        :disabled="lockUntilConsent && !consentsOk"
      >
        <template v-if="editing">{{ saving ? '저장 중…' : '저장' }}</template>
        <template v-else>{{ saving ? '등록 중…' : '등록' }}</template>
      </el-button>
    </div>
  </form>
</template>

<style scoped>
.cform {
  display: flex;
  flex-direction: column;
}
.cform__dup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--color-warning);
}
.cfield__error {
  margin: 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
.cform__consent-text {
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--color-subtle);
  font-size: var(--font-size-sm);
}
.cform__consent-text p {
  margin: 0 0 4px;
}
.cform__phone {
  max-width: 280px;
}
.cform__staff {
  max-width: 280px;
}
.cform__year {
  max-width: 120px;
}
.cform__md {
  width: 96px;
}
.cform__more {
  align-self: flex-start;
  margin: 8px 0;
  padding-left: 0;
}
.cform__address {
  display: grid;
  grid-template-columns: 130px 160px minmax(0, 1fr);
  gap: 8px;
}
.cform__size {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.cform__size + .cform__size {
  margin-top: 6px;
}
.cform__size-label {
  flex: 0 0 48px;
  padding-top: 4px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.cform__hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.cform__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}
@media (max-width: 767.98px) {
  .cform__address {
    grid-template-columns: 1fr 1fr;
  }
  .cform__detail {
    grid-column: 1 / -1;
  }
  .cform__phone,
  .cform__staff {
    max-width: none;
  }
  .cform__actions .el-button {
    flex: 1;
  }
}
</style>

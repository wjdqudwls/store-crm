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
})
const emit = defineEmits(['created', 'use-existing'])

const session = useSessionStore()
const form = reactive({ ...emptyForm(), staffId: props.presetStaffId, name: props.presetName })
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
    const res = await createCustomer(toRegistrationBody(form))
    emit('created', res)
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
  const base = { ...emptyForm(), staffId: props.presetStaffId, name: props.presetName }
  return JSON.stringify(form) !== JSON.stringify(base)
}

defineExpose({ isDirty, submit })
</script>

<template>
  <form ref="rootRef" class="cform" novalidate @submit.prevent="submit">
    <!-- 409 중복: 기존 정보는 바꾸지 않는다 (REG-05) -->
    <div v-if="duplicate" class="cform__dup" role="alert">
      <span>
        이미 등록된 고객입니다 · <strong>{{ duplicate.name }}</strong>
        {{ duplicate.phoneMasked }}
      </span>
      <el-button type="primary" size="small" @click="emit('use-existing', duplicate.customerId)">
        {{ inSale ? '이 고객으로 판매' : '기존 고객 보기' }}
      </el-button>
    </div>

    <!-- 기본 -->
    <section class="cform__group">
      <h3 class="cform__title">기본</h3>
      <div class="cform__row">
        <div class="cfield">
          <span class="cfield__label">내/외국인 <em>필수</em></span>
          <el-radio-group v-model="form.nationality">
            <el-radio-button value="DOMESTIC">내국인</el-radio-button>
            <el-radio-button value="FOREIGN">외국인</el-radio-button>
          </el-radio-group>
        </div>
        <div class="cfield cfield--muted">
          <span class="cfield__label">가입매장</span>
          <span>{{ session.me.name }}</span>
        </div>
      </div>

      <div class="cform__row">
        <div v-if="foreign" class="cfield" data-field="nameEn">
          <label class="cfield__label" for="cf-name-en">영문 이름 <em>필수</em></label>
          <el-input id="cf-name-en" v-model="form.nameEn" maxlength="100" placeholder="Aiko Sato" />
          <p v-if="errors.nameEn" class="cfield__error">{{ errors.nameEn }}</p>
        </div>
        <div class="cfield" data-field="name">
          <label class="cfield__label" for="cf-name">
            {{ foreign ? '한글 이름' : '이름' }} <em>{{ foreign ? '선택' : '필수' }}</em>
          </label>
          <el-input
            id="cf-name"
            v-model="form.name"
            maxlength="50"
            :placeholder="foreign ? '비우면 영문 이름을 씁니다' : '김하늘'"
          />
          <p v-if="errors.name" class="cfield__error">{{ errors.name }}</p>
        </div>
        <div class="cfield" data-field="phone">
          <label class="cfield__label" for="cf-phone">전화번호 <em>필수</em></label>
          <el-input
            id="cf-phone"
            :model-value="form.phone"
            :inputmode="foreign ? 'tel' : 'numeric'"
            :placeholder="foreign ? '+81 90 1234 5678' : '010-0000-0000'"
            @update:model-value="onPhoneInput"
          />
          <p v-if="errors.phone" class="cfield__error">{{ errors.phone }}</p>
        </div>
      </div>
    </section>

    <!-- 동의 (BR-08) -->
    <section class="cform__group">
      <h3 class="cform__title">동의</h3>
      <div class="cfield" data-field="privacy">
        <div class="cform__consent">
          <el-checkbox v-model="form.privacy">개인정보 수집·이용 동의 <em>필수</em></el-checkbox>
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
      <div class="cfield" data-field="age14">
        <el-checkbox v-model="form.age14">만 14세 이상입니다 <em>필수</em></el-checkbox>
        <p v-if="errors.age14" class="cfield__error">{{ errors.age14 }}</p>
      </div>
      <div class="cfield">
        <span class="cfield__label">마케팅 수신 <em>선택</em></span>
        <div class="cform__inline">
          <el-checkbox v-model="form.marketingSms">문자</el-checkbox>
          <el-checkbox v-model="form.marketingKakao">카톡</el-checkbox>
        </div>
      </div>
    </section>

    <!-- 직원 화면만 -->
    <section v-if="showStaff" class="cform__group">
      <div class="cfield" data-field="staffId">
        <label class="cfield__label" for="cf-staff">담당 직원 <em>선택</em></label>
        <StaffSelect id="cf-staff" v-model="form.staffId" placeholder="담당 직원을 고르세요" />
        <p v-if="errors.staffId" class="cfield__error">{{ errors.staffId }}</p>
      </div>
    </section>

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

    <section v-show="showOptional" class="cform__group">
      <h3 class="cform__title">선택</h3>
      <div class="cform__row">
        <div class="cfield cfield--narrow" data-field="birthYear">
          <label class="cfield__label" for="cf-year">생년</label>
          <el-input
            id="cf-year"
            v-model="form.birthYear"
            inputmode="numeric"
            maxlength="4"
            placeholder="1991"
          />
          <p v-if="errors.birthYear" class="cfield__error">{{ errors.birthYear }}</p>
        </div>
        <div class="cfield" data-field="birthMd">
          <span class="cfield__label">생일</span>
          <div class="cform__inline">
            <el-select v-model="form.birthMonth" placeholder="월" clearable class="cform__md">
              <el-option v-for="m in months" :key="m" :label="`${m}월`" :value="m" />
            </el-select>
            <el-select v-model="form.birthDay" placeholder="일" clearable class="cform__md">
              <el-option v-for="d in days" :key="d" :label="`${d}일`" :value="d" />
            </el-select>
          </div>
          <p v-if="errors.birthMd" class="cfield__error">{{ errors.birthMd }}</p>
        </div>
      </div>

      <div class="cfield">
        <span class="cfield__label">주소</span>
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

      <div class="cfield">
        <span class="cfield__label">성별</span>
        <ChoiceButtons v-model="form.gender" :options="genderOptions" />
      </div>

      <div class="cfield">
        <span class="cfield__label">선호 품목 <em>여러 개</em></span>
        <ChoiceButtons v-model="form.preferredItemTypes" :options="itemTypeOptions" multiple />
      </div>

      <div class="cfield">
        <span class="cfield__label">사이즈</span>
        <p v-if="!form.gender" class="cform__hint">
          성별을 고르면 아우터·하의 사이즈도 고를 수 있어요
        </p>
        <div v-for="f in sizeFields" :key="f.key" class="cform__size">
          <span class="cform__size-label">{{ f.label }}</span>
          <ChoiceButtons v-model="form.sizes[f.key]" :options="f.options" size="small" />
        </div>
      </div>

      <div class="cfield">
        <span class="cfield__label">연락 채널</span>
        <ChoiceButtons v-model="form.contactChannel" :options="channelOptions" />
      </div>
    </section>

    <div class="cform__actions">
      <slot name="actions" />
      <span v-if="!consentsOk" class="cform__hint">필수 동의 2개에 체크해 주세요</span>
      <el-button type="primary" size="large" native-type="submit" :loading="saving">
        {{ saving ? '등록 중…' : '등록' }}
      </el-button>
    </div>
  </form>
</template>

<style scoped>
.cform {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.cform__group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cform__title {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 6px;
}
.cform__row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
}
.cform__row > .cfield {
  flex: 1 1 200px;
}
.cform__row > .cfield--narrow {
  flex: 0 1 120px;
}
.cfield {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cfield--muted {
  color: var(--color-text-muted);
}
.cfield__label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.cfield__label em,
.cform em {
  margin-left: 4px;
  font-style: normal;
  font-weight: var(--font-weight-regular);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.cfield__error {
  margin: 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
.cform__dup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
}
.cform__consent {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cform__consent-text {
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--color-subtle);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}
.cform__consent-text p {
  margin: 0;
}
.cform__inline {
  display: flex;
  gap: 8px 16px;
  flex-wrap: wrap;
}
.cform__more {
  align-self: flex-start;
  padding-left: 0;
}
.cform__md {
  width: 100px;
}
.cform__address {
  display: grid;
  grid-template-columns: 120px 160px 1fr;
  gap: 8px;
}
.cform__size {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.cform__size-label {
  flex: 0 0 48px;
  padding-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
.cform__hint {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
.cform__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

@media (max-width: 767.98px) {
  .cform__address {
    grid-template-columns: 1fr 1fr;
  }
  .cform__detail {
    grid-column: 1 / -1;
  }
  .cform__actions .el-button {
    flex: 1;
  }
}
</style>

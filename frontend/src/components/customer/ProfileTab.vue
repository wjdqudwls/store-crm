<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerForm from '@/components/CustomerForm.vue'
import { getCustomer, updateCustomer } from '@/api/customers'
import { formFromCustomer } from '@/features/customer/customerForm'
import { useSessionStore } from '@/stores/session'
import { useIsPhone } from '@/composables/useIsPhone'
import { channelLabels, genderLabels } from '@/features/customer/customerDetail'
import { itemTypeLabels, sizeLabel } from '@/utils/format'

/**
 * 프로필 (05 SCR-06, CUS-04). 상세주소는 가려 두고 [상세주소 보기]를 누를 때만 불러온다 (BR-08).
 * [수정](매장·본사)은 다음 작업에서 등록 폼을 다시 써서 만든다.
 */
const props = defineProps({ customer: { type: Object, required: true } })
const emit = defineEmits(['updated'])
const router = useRouter()
const session = useSessionStore()
const isPhone = useIsPhone()

// ---- [수정]: 매장 계정·본사 (CUS-04). 등록 폼을 그대로 쓰고 필수 동의만 숨긴다 ----
const canEdit = computed(() => session.isStore || session.isHq)
const editOpen = ref(false)
const initial = ref(null)
const formRef = ref()
async function openEdit() {
  const withAddress = await getCustomer(props.customer.id, { includeAddress: true })
  initial.value = formFromCustomer(withAddress, withAddress.addressDetail)
  editOpen.value = true
}
async function beforeClose(done) {
  if (!formRef.value?.isDirty()) return done()
  try {
    await ElMessageBox.confirm('고친 내용을 버리고 닫을까요?', '프로필 수정 닫기', {
      confirmButtonText: '버리고 닫기',
      cancelButtonText: '계속 고치기',
      type: 'warning',
    })
    done()
  } catch {
    // 계속 고치기
  }
}
function onSaved() {
  editOpen.value = false
  address.value = null
  ElMessage.success('프로필을 저장했습니다')
  emit('updated')
}
function openOther(customerId) {
  editOpen.value = false
  router.push(`/customers/${customerId}?tab=profile`)
}
const p = computed(() => props.customer.profile)

const address = ref(null)
const loadingAddress = ref(false)
async function showAddress() {
  loadingAddress.value = true
  address.value = (await getCustomer(props.customer.id, { includeAddress: true })).addressDetail
  loadingAddress.value = false
}

const birthday = computed(() => {
  const md = p.value.birthMd
  const md2 = md ? `${Number(md.slice(0, 2))}월 ${Number(md.slice(2))}일` : null
  return [p.value.birthYear ? `${p.value.birthYear}년` : null, md2].filter(Boolean).join(' ') || '-'
})
const region = computed(
  () => [p.value.regionSido, p.value.regionGu].filter(Boolean).join(' ') || '-',
)
const target = computed(() => ({ F: 'WOMEN', M: 'MEN' })[p.value.gender])
const sizes = computed(
  () =>
    [
      ['top', '상의', 'TOP'],
      ['outer', '아우터', 'OUTER'],
      ['bottom', '하의', 'BOTTOM'],
      ['shoes', '신발', 'SHOES'],
    ]
      .filter(([k]) => p.value.sizes?.[k])
      .map(
        ([k, label, itemType]) => `${label} ${sizeLabel(p.value.sizes[k], itemType, target.value)}`,
      )
      .join(' · ') || '-',
)
const marketing = computed(() => {
  const m = props.customer.marketing
  const on = [m.sms && '문자', m.kakao && '카톡'].filter(Boolean)
  return on.length ? `${on.join(' · ')} 동의` : '동의 안 함'
})
</script>

<template>
  <div v-if="canEdit" class="prof__bar">
    <el-button size="small" @click="openEdit">수정</el-button>
  </div>
  <el-dialog
    v-model="editOpen"
    title="프로필 수정"
    width="min(960px, 96vw)"
    :fullscreen="isPhone"
    :before-close="beforeClose"
    :close-on-click-modal="false"
    destroy-on-close
    align-center
  >
    <CustomerForm
      v-if="initial"
      ref="formRef"
      mode="edit"
      :initial="initial"
      :collapse-optional="false"
      :submit-fn="(body) => updateCustomer(customer.id, body)"
      @saved="onSaved"
      @use-existing="openOther"
    />
  </el-dialog>
  <div class="ftable">
    <div class="ft-label">내/외국인</div>
    <div class="ft-value">
      {{ customer.nationality === 'FOREIGN' ? '외국인' : '내국인' }}
      <template v-if="customer.nameEn"> · {{ customer.nameEn }}</template>
    </div>
    <div class="ft-label">가입매장</div>
    <div class="ft-value">{{ customer.homeStore.name }}</div>

    <div class="ft-label">생년·생일</div>
    <div class="ft-value">{{ birthday }}</div>
    <div class="ft-label">성별</div>
    <div class="ft-value">{{ genderLabels[p.gender] ?? '-' }}</div>

    <div class="ft-label">주소</div>
    <div class="ft-value ft-wide">
      <div class="ft-inline">
        <span>{{ region }}</span>
        <span v-if="address !== null">{{ address || '상세주소 없음' }}</span>
        <el-button
          v-else
          link
          type="primary"
          size="small"
          :loading="loadingAddress"
          @click="showAddress"
        >
          상세주소 보기
        </el-button>
      </div>
    </div>

    <div class="ft-label">선호 품목</div>
    <div class="ft-value">
      {{ p.preferredItemTypes.map((t) => itemTypeLabels[t]).join(' · ') || '-' }}
    </div>
    <div class="ft-label">연락 채널</div>
    <div class="ft-value">{{ channelLabels[p.contactChannel] ?? '-' }}</div>

    <div class="ft-label">사이즈</div>
    <div class="ft-value">{{ sizes }}</div>
    <div class="ft-label">마케팅 수신</div>
    <div class="ft-value">{{ marketing }}</div>
  </div>
</template>

<style scoped>
.prof__bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
</style>

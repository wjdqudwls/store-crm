<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import CustomerSearch from '@/components/CustomerSearch.vue'
import GradeBadge from '@/components/GradeBadge.vue'
import ReturnPicker from './ReturnPicker.vue'
import { getCustomerForSale, getStaff, returnSale } from '@/api/sales'
import { refundTotal } from '@/features/sale/returnAmount'
import { useSaleShortcuts } from '@/composables/useSaleShortcuts'
import { formatWon } from '@/utils/format'

/**
 * 판매 기록의 환불 모드 (05 SCR-02, SAL-03). 처리 직원 → 고객(회원만) → 구매 목록에서 상품명으로 찾기 →
 * 한 영수증의 줄마다 반품 수량 → [환불 기록] → 확인 창 → `POST /sales/{id}/returns`.
 * 비회원 판매는 판매 내역에서 날짜로 찾는다.
 */
const props = defineProps({
  dayClosed: { type: Boolean, default: false },
  phone: { type: Boolean, default: false },
})
const emit = defineEmits(['day-closed'])
const router = useRouter()

const staffId = ref(null)
const staffList = ref([])
getStaff()
  .then((v) => (staffList.value = v))
  .catch(() => {})
const staffName = computed(() => staffList.value.find((s) => s.id === staffId.value)?.name)

const customer = ref(null)
async function selectCustomer(c) {
  customer.value = await getCustomerForSale(c.id)
}

const selection = ref(null)
const pickerRef = ref()
const refund = computed(() =>
  selection.value ? refundTotal(selection.value.sale.lines, selection.value.qty) : 0,
)
const pickedLines = computed(() =>
  selection.value
    ? selection.value.sale.lines
        .filter((l) => selection.value.qty[l.lineId])
        .map((l) => ({ ...l, returnQty: selection.value.qty[l.lineId] }))
    : [],
)
const pickedQty = computed(() => pickedLines.value.reduce((n, l) => n + l.returnQty, 0))

// ---- 저장 ----
let idempotencyKey = crypto.randomUUID()
const errors = ref({})
const confirmOpen = ref(false)
const saving = ref(false)

function requestSave() {
  if (props.dayClosed || saving.value) return
  const e = {}
  if (!staffId.value) e.staff = '처리 직원을 고르세요'
  if (!selection.value) e.lines = '돌려받을 상품의 반품 수량을 넣으세요'
  errors.value = e
  if (!Object.keys(e).length) confirmOpen.value = true
}

async function save() {
  if (saving.value) return
  saving.value = true
  const body = {
    staffId: staffId.value,
    lines: pickedLines.value.map((l) => ({ lineId: l.lineId, qty: l.returnQty })),
  }
  try {
    const res = await returnSale(selection.value.sale.saleId, body, idempotencyKey)
    ElMessage.success({
      message: `환불이 기록되었습니다 · ${formatWon(res.returnedAmount)}`,
      duration: 4000,
    })
    idempotencyKey = crypto.randomUUID() // 저장에 성공했을 때만 새 키 (BR-12)
    staffId.value = null
    errors.value = {}
    pickerRef.value?.reload() // 줄 목록을 다시 불러와 줄 긋기
  } catch (err) {
    const code = err?.body?.code
    if (code === 'DAY_CLOSED') emit('day-closed')
    if (code === 'RETURN_QTY_EXCEEDED') {
      ElMessage.error('다른 화면에서 먼저 반품됐을 수 있습니다. 남은 수량을 다시 불러왔습니다')
      pickerRef.value?.reload()
    } else {
      ElMessage.error(
        err?.body?.message ??
          '저장됐는지 확인하지 못했습니다. 다시 누르면 같은 요청으로 다시 보냅니다',
      )
    }
  } finally {
    saving.value = false
    confirmOpen.value = false
  }
}

useSaleShortcuts({ confirmOpen, onSave: save, onRequest: requestSave })
</script>

<template>
  <div class="sp" :class="{ 'sp--phone': phone }">
    <div class="sp__main">
      <div class="ftable">
        <label class="ft-label is-required" for="refund-staff">처리 직원</label>
        <div class="ft-value">
          <StaffSelect id="refund-staff" v-model="staffId" placeholder="처리 직원을 고르세요" />
          <p v-if="errors.staff" class="field-error">{{ errors.staff }}</p>
        </div>
        <div class="ft-label is-required">고객</div>
        <div class="ft-value">
          <div v-if="customer" class="ft-inline">
            <strong>{{ customer.name }}</strong>
            <span class="muted">{{ customer.phoneMasked }}</span>
            <GradeBadge :grade="customer.grade.current" />
            <el-button link size="small" @click="customer = null">바꾸기</el-button>
          </div>
          <CustomerSearch v-else members-only @select="selectCustomer" />
        </div>
      </div>
      <p class="sp__note">
        비회원 판매는 판매 내역에서 날짜로 찾아 영수증의 [반품]을 눌러 주세요
        <el-button link type="primary" @click="router.push('/sales')">판매 내역</el-button>
      </p>

      <h2 class="sp__sub">돌려받을 상품</h2>
      <ReturnPicker ref="pickerRef" v-model="selection" :customer-id="customer?.id ?? null" />
      <p v-if="errors.lines" class="field-error">{{ errors.lines }}</p>
    </div>

    <aside class="sp__side" aria-label="환불 요약">
      <div class="ftable ftable--2">
        <div class="ft-caption">환불 내역</div>
        <div class="ft-label">처리 직원</div>
        <div class="ft-value">{{ staffName ?? '-' }}</div>
        <div class="ft-label">영수증</div>
        <div class="ft-value">{{ selection?.sale.receiptNo ?? '-' }}</div>
        <div class="ft-label">반품 수량</div>
        <div class="ft-value">{{ pickedQty }}개</div>
        <div class="ft-label">환불 예상</div>
        <div class="ft-value sp__sum">{{ formatWon(refund) }}</div>
      </div>
      <p class="sp__hint">최종 환불 금액은 저장한 뒤 서버가 계산한 값입니다</p>
      <el-button
        type="primary"
        size="large"
        class="sp__save"
        :disabled="dayClosed"
        :loading="saving"
        @click="requestSave"
      >
        {{ saving ? '저장 중…' : '환불 기록' }}
        <span v-if="!phone" class="sp__shortcut">Ctrl+Enter</span>
      </el-button>
    </aside>

    <el-dialog
      v-model="confirmOpen"
      title="환불을 기록할까요?"
      width="min(480px, 92vw)"
      align-center
    >
      <dl class="sp__confirm">
        <dt>처리 직원</dt>
        <dd>{{ staffName }}</dd>
        <dt>고객</dt>
        <dd>{{ customer ? `${customer.name} ${customer.phoneMasked}` : '-' }}</dd>
        <dt>영수증</dt>
        <dd>{{ selection?.sale.receiptNo }}</dd>
        <dt>상품</dt>
        <dd>
          <div v-for="l in pickedLines" :key="l.lineId">
            {{ l.productName }} {{ l.color ?? '' }}/{{ l.size }} × {{ l.returnQty }}
          </div>
        </dd>
        <dt>환불 금액</dt>
        <dd class="sp__confirm-total">{{ formatWon(refund) }}</dd>
      </dl>
      <template #footer>
        <el-button @click="confirmOpen = false">돌아가기 (Esc)</el-button>
        <el-button type="primary" :loading="saving" @click="save">환불 기록 (Enter)</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped src="./salePanel.css"></style>

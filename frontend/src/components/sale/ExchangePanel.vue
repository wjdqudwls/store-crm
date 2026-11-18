<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import CustomerSearch from '@/components/CustomerSearch.vue'
import GradeBadge from '@/components/GradeBadge.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import ReturnPicker from './ReturnPicker.vue'
import SaleLines from './SaleLines.vue'
import { exchangeSale, getCustomerForSale, getSale, getStaff } from '@/api/sales'
import { exchangeDiff, refundTotal } from '@/features/sale/returnAmount'
import { useSaleLines } from '@/composables/useSaleLines'
import { useSaleShortcuts } from '@/composables/useSaleShortcuts'
import { formatWon } from '@/utils/format'

/**
 * 판매 기록의 교환 모드 (05 SCR-02, SAL-06, S13). 위에 돌려받을 상품(환불과 같은 찾기, 한 영수증),
 * 아래에 새 상품(구매와 같은 상품 고르기). 처리 직원이 새 상품의 판매 사원이다 (03 4.8).
 * 영수증 상세의 [교환]으로 들어오면(saleId) 그 영수증에서 고른다. 비회원 판매도 이 길로 교환한다.
 */
const props = defineProps({
  dayClosed: { type: Boolean, default: false },
  phone: { type: Boolean, default: false },
  saleId: { type: Number, default: null },
})
const emit = defineEmits(['day-closed'])
const router = useRouter()

const staffId = ref(null)
const staffList = ref([])
getStaff().then((v) => (staffList.value = v))
const staffName = computed(() => staffList.value.find((s) => s.id === staffId.value)?.name)

// ---- 고객 (영수증으로 들어오면 그 영수증의 고객, 비회원이면 null) ----
const customer = ref(null)
const fixedSale = ref(null)
async function selectCustomer(c) {
  customer.value = await getCustomerForSale(c.id)
}
watch(
  () => props.saleId,
  async (id) => {
    fixedSale.value = id ? await getSale(id) : null
    customer.value = fixedSale.value?.customerId
      ? await getCustomerForSale(fixedSale.value.customerId)
      : null
  },
  { immediate: true },
)

// ---- 돌려받을 상품 ----
const selection = ref(null)
const pickerRef = ref()
const returned = computed(() =>
  selection.value ? refundTotal(selection.value.sale.lines, selection.value.qty) : 0,
)
const pickedLines = computed(() =>
  selection.value
    ? selection.value.sale.lines
        .filter((l) => selection.value.qty[l.lineId])
        .map((l) => ({ ...l, returnQty: selection.value.qty[l.lineId] }))
    : [],
)

// ---- 새 상품 (할인은 판매 전 등급, BR-10) ----
const discountRate = computed(() => customer.value?.grade.discountRate ?? 0)
const members = computed(() => customer.value?.members ?? [])
const {
  lines,
  sum,
  invalid,
  addLine,
  removeLine,
  changeQty,
  changeAmount,
  changeWearer,
  reset: resetLines,
  toBody,
} = useSaleLines(discountRate)

const diff = computed(() => exchangeDiff(returned.value, sum.value.amount))
const diffLabel = computed(
  () =>
    ({ EXTRA: '추가 결제', REFUND: '차액 환불', EVEN: '차액 없음' })[diff.value.kind] ??
    '차액 없음',
)

// ---- 저장 ----
let idempotencyKey = crypto.randomUUID()
const errors = ref({})
const confirmOpen = ref(false)
const saving = ref(false)

function requestSave() {
  if (props.dayClosed || saving.value) return
  const e = {}
  if (!staffId.value) e.staff = '처리 직원을 고르세요'
  if (!selection.value) e.returns = '돌려받을 상품의 반품 수량을 넣으세요'
  if (!lines.value.length) e.lines = '새 상품을 1개 이상 담으세요'
  else if (invalid.value) e.lines = '결제 금액을 입력하세요'
  errors.value = e
  if (!Object.keys(e).length) confirmOpen.value = true
}

async function save() {
  if (saving.value) return
  saving.value = true
  const body = {
    staffId: staffId.value,
    returnLines: pickedLines.value.map((l) => ({ lineId: l.lineId, qty: l.returnQty })),
    newLines: toBody(),
  }
  try {
    const res = await exchangeSale(selection.value.sale.saleId, body, idempotencyKey)
    idempotencyKey = crypto.randomUUID() // 저장에 성공했을 때만 새 키 (BR-12)
    ElMessage.success({
      message: `교환이 기록되었습니다 · 새 영수증 ${res.newSale.receiptNo}`,
      duration: 5000,
    })
    staffId.value = null
    resetLines()
    errors.value = {}
    pickerRef.value?.reload()
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
        <label class="ft-label is-required" for="exchange-staff">처리 직원</label>
        <div class="ft-value">
          <StaffSelect id="exchange-staff" v-model="staffId" placeholder="처리 직원을 고르세요" />
          <p v-if="errors.staff" class="field-error">{{ errors.staff }}</p>
        </div>
        <div class="ft-label" :class="{ 'is-required': !saleId }">고객</div>
        <div class="ft-value">
          <div v-if="saleId" class="ft-inline">
            <template v-if="customer">
              <strong>{{ customer.name }}</strong>
              <span class="muted">{{ customer.phoneMasked }}</span>
              <GradeBadge :grade="customer.grade.current" />
            </template>
            <span v-else>비회원</span>
          </div>
          <div v-else-if="customer" class="ft-inline">
            <strong>{{ customer.name }}</strong>
            <span class="muted">{{ customer.phoneMasked }}</span>
            <GradeBadge :grade="customer.grade.current" />
            <el-button link size="small" @click="customer = null">바꾸기</el-button>
          </div>
          <CustomerSearch v-else members-only @select="selectCustomer" />
        </div>
      </div>
      <p class="sp__note">
        교환은 새 구매로 세지 않습니다 (티켓은 그대로, 금액만 바뀝니다)
        <template v-if="saleId">
          ·
          <el-button
            link
            type="primary"
            @click="router.replace({ path: '/sales/new', query: { mode: 'EXCHANGE' } })"
            >다른 고객의 구매에서 찾기</el-button
          >
        </template>
        <template v-else>
          · 비회원 판매는 판매 내역의 영수증에서 [교환]을 눌러 주세요
          <el-button link type="primary" @click="router.push('/sales')">판매 내역</el-button>
        </template>
      </p>

      <h2 class="sp__sub">① 돌려받을 상품</h2>
      <ReturnPicker
        ref="pickerRef"
        v-model="selection"
        :customer-id="saleId ? null : (customer?.id ?? null)"
        :sale-id="saleId"
      />
      <p v-if="errors.returns" class="field-error">{{ errors.returns }}</p>

      <h2 class="sp__sub">② 새 상품</h2>
      <section v-if="phone" class="sp__card">
        <ProductPicker @add="addLine" />
      </section>
      <ProductPicker v-else layout="row" @add="addLine" />
      <SaleLines
        :lines="lines"
        :members="members"
        :phone="phone"
        kind-label="교환"
        empty-text="교환해 줄 새 상품을 찾아 [담기]를 누르면 여기에 나옵니다"
        @qty="changeQty"
        @amount="changeAmount"
        @wearer="changeWearer"
        @remove="removeLine"
      />
      <p v-if="errors.lines" class="field-error">{{ errors.lines }}</p>
    </div>

    <aside class="sp__side" aria-label="교환 요약">
      <div class="ftable ftable--2">
        <div class="ft-caption">교환 내역</div>
        <div class="ft-label">처리 직원</div>
        <div class="ft-value">{{ staffName ?? '-' }}</div>
        <div class="ft-label">원 영수증</div>
        <div class="ft-value">{{ selection?.sale.receiptNo ?? '-' }}</div>
        <div class="ft-label">반품 금액</div>
        <div class="ft-value sp__minus">− {{ formatWon(returned) }}</div>
        <div class="ft-label">새 상품 합계</div>
        <div class="ft-value">+ {{ formatWon(sum.amount) }}</div>
        <div class="ft-label">{{ diffLabel }}</div>
        <div class="ft-value sp__sum">{{ formatWon(diff.amount) }}</div>
      </div>
      <p class="sp__hint">처리 직원이 새 상품의 판매 사원이 됩니다</p>
      <el-button
        type="primary"
        size="large"
        class="sp__save"
        :disabled="dayClosed"
        :loading="saving"
        @click="requestSave"
      >
        {{ saving ? '저장 중…' : '교환 기록' }}
        <span v-if="!phone" class="sp__shortcut">Ctrl+Enter</span>
      </el-button>
    </aside>

    <el-dialog
      v-model="confirmOpen"
      title="교환을 기록할까요?"
      width="min(520px, 92vw)"
      align-center
    >
      <dl class="sp__confirm">
        <dt>처리 직원</dt>
        <dd>{{ staffName }}</dd>
        <dt>고객</dt>
        <dd>{{ customer ? `${customer.name} ${customer.phoneMasked}` : '비회원' }}</dd>
        <dt>돌려받음</dt>
        <dd>
          <div v-for="l in pickedLines" :key="l.lineId">
            {{ l.productName }} {{ l.color ?? '' }}/{{ l.size }} × {{ l.returnQty }}
          </div>
          <span class="muted">{{ selection?.sale.receiptNo }} · − {{ formatWon(returned) }}</span>
        </dd>
        <dt>새 상품</dt>
        <dd>
          <div v-for="l in lines" :key="l.key">
            {{ l.productName }} {{ l.color ?? '' }}/{{ l.size }} × {{ l.qty }} ·
            {{ formatWon(l.lineAmount) }}
          </div>
        </dd>
        <dt>{{ diffLabel }}</dt>
        <dd class="sp__confirm-total">{{ formatWon(diff.amount) }}</dd>
      </dl>
      <template #footer>
        <el-button @click="confirmOpen = false">돌아가기 (Esc)</el-button>
        <el-button type="primary" :loading="saving" @click="save">교환 기록 (Enter)</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped src="./salePanel.css"></style>
<style scoped>
.sp__card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
}
</style>

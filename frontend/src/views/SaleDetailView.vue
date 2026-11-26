<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import GradeBadge from '@/components/GradeBadge.vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import StaffSelect from '@/components/StaffSelect.vue'
import { getSale, returnSale, voidSale } from '@/api/sales'
import { useSaleChangeRules } from '@/composables/useSaleChangeRules'
import { useSessionStore } from '@/stores/session'
import { dateTimeLabel } from '@/features/customer/customerDetail'
import { leftQty, refundTotal } from '@/features/sale/returnAmount'
import { formatWon, sizeLabel } from '@/utils/format'

/**
 * 영수증 상세 `/sales/{id}` (05 SCR-03, SAL-03·06·07). 버튼은 할 수 있을 때만 보인다 (W8).
 * [교환]은 판매 기록의 교환 모드를 이 영수증으로, [정정]은 정정 화면(`/sales/{id}/edit`)을 연다.
 */
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const sale = ref(null)
const notFound = ref(false)
const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    notFound.value = false
    try {
      sale.value = await getSale(Number(route.params.id))
    } catch (e) {
      if (e?.status === 404) notFound.value = true
      else throw e
    }
  })
watch(() => route.params.id, load, { immediate: true })

const statusLabels = {
  COMPLETED: '정상',
  PARTIAL_RETURNED: '일부 반품',
  RETURNED: '전체 반품',
  VOIDED: '기록 취소',
}
const statusType = {
  COMPLETED: 'success',
  PARTIAL_RETURNED: 'warning',
  RETURNED: 'danger',
  VOIDED: 'info',
}
const returnKindLabels = { RETURN: '반품', EXCHANGE: '교환', VOID: '기록 취소' }
const kindLabels = { EXCHANGE: '교환', CORRECTION: '정정' }

const left = leftQty
const hasLeft = computed(() => sale.value?.lines.some((l) => left(l) > 0))
const { canChange, canReturn: canReturnRule, isOwnStore } = useSaleChangeRules()
const changeable = computed(() => sale.value && canChange(sale.value))
// 매장 계정은 자기 매장 판매만 고친다. 다른 매장 영수증은 보기만 한다
const otherStore = computed(() => session.isStore && sale.value && !isOwnStore(sale.value))
const canReturn = computed(
  () => canReturnRule(sale.value) && sale.value.status !== 'VOIDED' && hasLeft.value,
)
// 정정·기록 취소: 매장 계정은 판매한 날·마감 전, 본사는 언제나. 남은 줄이 있어야 한다 (BR-16)
const canVoid = computed(() => changeable.value && sale.value.status !== 'VOIDED' && hasLeft.value)

function openExchange() {
  router.push({ path: '/sales/new', query: { mode: 'EXCHANGE', saleId: sale.value.id } })
}

// ---- 반품 창 ----
const returnOpen = ref(false)
const returnStaff = ref(null)
const returnQty = ref({})
const returnError = ref('')
const saving = ref(false)
let returnKey = crypto.randomUUID()
function openReturn() {
  returnKey = crypto.randomUUID() // 반품 창을 열 때 요청 키 (BR-12)
  returnStaff.value = null
  returnQty.value = Object.fromEntries(sale.value.lines.map((l) => [l.lineId, 0]))
  returnError.value = ''
  returnOpen.value = true
}
const refundPreview = computed(() => refundTotal(sale.value?.lines ?? [], returnQty.value))
async function saveReturn() {
  const lines = Object.entries(returnQty.value)
    .filter(([, q]) => q > 0)
    .map(([lineId, qty]) => ({ lineId: Number(lineId), qty }))
  if (!returnStaff.value) return (returnError.value = '처리 직원을 골라 주세요')
  if (!lines.length) return (returnError.value = '반품할 수량을 1개 이상 넣어 주세요')
  saving.value = true
  try {
    const res = await returnSale(sale.value.id, { staffId: returnStaff.value, lines }, returnKey)
    returnOpen.value = false
    ElMessage.success(`${formatWon(res.returnedAmount)} 반품했습니다`)
    load()
  } catch (e) {
    returnError.value = e?.body?.message ?? '반품됐는지 확인하지 못했습니다'
    if (e?.status === 409) load()
  } finally {
    saving.value = false
  }
}

// ---- 기록 취소 창 ----
const voidOpen = ref(false)
const voidStaff = ref(null)
const voidError = ref('')
let voidKey = crypto.randomUUID()
function openVoid() {
  voidKey = crypto.randomUUID() // 기록 취소 창을 열 때 요청 키. 실패 뒤 다시 누르면 같은 키 (BR-12)
  voidStaff.value = null
  voidError.value = ''
  voidOpen.value = true
}
async function saveVoid() {
  if (session.isStore && !voidStaff.value) return (voidError.value = '처리 직원을 골라 주세요')
  saving.value = true
  try {
    await voidSale(sale.value.id, voidStaff.value ? { staffId: voidStaff.value } : {}, voidKey)
    voidOpen.value = false
    ElMessage.success('기록 취소했습니다')
    load()
  } catch (e) {
    voidError.value = e?.body?.message ?? '처리됐는지 확인하지 못했습니다'
    if (e?.status === 409) load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-button link class="sd__back" @click="router.back()">‹ 판매 내역</el-button>

    <div v-if="notFound" class="card empty">
      찾을 수 없습니다.
      <el-button link type="primary" @click="router.push('/sales')">판매 내역으로</el-button>
    </div>
    <LoadError v-else-if="error && !sale" :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-else-if="!sale" animated :rows="6" class="card" />

    <template v-else>
      <header class="card sd__head">
        <div class="sd__title">
          <h1 class="sd__no">{{ sale.receiptNo }}</h1>
          <el-tag :type="statusType[sale.status]">{{ statusLabels[sale.status] }}</el-tag>
          <el-tag v-if="kindLabels[sale.kind]" type="primary" effect="plain">
            {{ kindLabels[sale.kind] }}
          </el-tag>
          <div v-if="canReturn || canVoid" class="sd__btns">
            <el-button v-if="canReturn" type="primary" @click="openReturn">반품</el-button>
            <el-button v-if="canReturn" @click="openExchange">교환</el-button>
            <el-button v-if="canVoid" @click="router.push(`/sales/${sale.id}/edit`)"
              >정정</el-button
            >
            <el-button v-if="canVoid" type="danger" plain @click="openVoid">기록 취소</el-button>
          </div>
        </div>
        <div class="ftable">
          <div class="ft-label">판매 시각</div>
          <div class="ft-value">{{ dateTimeLabel(sale.soldAt, new Date(), true) }}</div>
          <div class="ft-label">매장</div>
          <div class="ft-value">{{ sale.store.name }}</div>
          <div class="ft-label">판매 사원</div>
          <div class="ft-value">{{ sale.sellerName }}</div>
          <div class="ft-label">고객</div>
          <div class="ft-value">
            <div class="ft-inline">
              <router-link
                v-if="sale.customerId"
                :to="{ path: `/customers/${sale.customerId}`, query: { from: 'receipt' } }"
              >
                {{ sale.customerName }}
              </router-link>
              <span v-else>비회원</span>
              <GradeBadge v-if="sale.appliedGrade" :grade="sale.appliedGrade" />
            </div>
          </div>
          <template v-if="sale.originSaleId">
            <div class="ft-label">원 영수증</div>
            <div class="ft-value ft-wide">
              <router-link :to="`/sales/${sale.originSaleId}`">{{
                sale.originReceiptNo
              }}</router-link>
            </div>
          </template>
          <div class="ft-label">수량 · 합계</div>
          <div class="ft-value ft-wide">
            <span
              >{{ sale.totalQty }}개 · <strong>{{ formatWon(sale.totalAmount) }}</strong></span
            >
          </div>
        </div>
        <p
          v-if="!(canReturn || canVoid) && session.isStore && sale.status !== 'VOIDED'"
          class="muted"
        >
          {{
            otherStore
              ? `다른 매장(${sale.store.name}) 판매입니다. 그 매장에서 환불·교환·정정합니다 (보기만).`
              : hasLeft
                ? '판매한 날이 지났거나 마감된 판매는 본사가 고칩니다.'
                : '전부 반품된 판매입니다.'
          }}
        </p>
      </header>

      <section class="card">
        <el-table :data="sale.lines">
          <el-table-column label="상품" min-width="200">
            <template #default="{ row }">
              <span :class="{ strike: row.returnedQty === row.qty }">{{ row.productName }}</span>
              <div class="muted">
                {{ row.productCode ?? '직접 입력' }} · {{ row.color ?? '-' }} /
                {{ sizeLabel(row.size, row.itemType, null) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="착용자" width="90">
            <template #default="{ row }">{{ row.wearerLabel ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="수량" width="70" align="right" />
          <el-table-column label="반품" width="80" align="right">
            <template #default="{ row }">
              <span v-if="row.returnedQty" class="sd__returned">{{ row.returnedQty }}개</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="정가" width="120" align="right">
            <template #default="{ row }">{{ formatWon(row.listPrice) }}</template>
          </el-table-column>
          <el-table-column label="결제 금액" width="130" align="right">
            <template #default="{ row }">{{ formatWon(row.lineAmount) }}</template>
          </el-table-column>
        </el-table>
        <div class="sd__total">
          <span>{{ sale.totalQty }}개</span>
          <strong>{{ formatWon(sale.totalAmount) }}</strong>
        </div>
      </section>

      <section v-if="sale.returns.length" class="card">
        <h2 class="sd__sub">반품·교환·기록 취소</h2>
        <ul class="sd__returns">
          <li v-for="r in sale.returns" :key="r.returnId">
            <el-tag size="small" :type="r.kind === 'VOID' ? 'info' : 'warning'">
              {{ returnKindLabels[r.kind] }}
            </el-tag>
            {{ dateTimeLabel(r.returnedAt) }} · {{ r.staffName }} · {{ formatWon(r.amount) }}
            <router-link v-if="r.newSaleId" :to="`/sales/${r.newSaleId}`">
              {{ r.kind === 'VOID' ? '정정 영수증' : '새 영수증' }} {{ r.newReceiptNo }}
            </router-link>
          </li>
        </ul>
      </section>
    </template>

    <!-- 반품 창 -->
    <el-dialog v-model="returnOpen" title="반품" width="min(560px, 94vw)" align-center>
      <div v-if="sale" class="rt">
        <StaffSelect v-model="returnStaff" placeholder="처리 직원" />
        <div v-for="l in sale.lines" :key="l.lineId" class="rt__line">
          <span class="rt__name">
            {{ l.productName }}
            <span class="muted">남은 {{ left(l) }}개 · {{ formatWon(l.lineAmount) }}</span>
          </span>
          <el-input-number
            v-model="returnQty[l.lineId]"
            :min="0"
            :max="left(l)"
            :disabled="!left(l)"
            size="small"
          />
        </div>
        <p class="rt__preview">
          환불 예상 <strong>{{ formatWon(refundPreview) }}</strong>
        </p>
        <p v-if="returnError" class="field-error">{{ returnError }}</p>
      </div>
      <template #footer>
        <el-button @click="returnOpen = false">돌아가기</el-button>
        <el-button type="primary" :loading="saving" @click="saveReturn">반품</el-button>
      </template>
    </el-dialog>

    <!-- 기록 취소 창 -->
    <el-dialog v-model="voidOpen" title="기록 취소" width="min(480px, 94vw)" align-center>
      <div class="rt">
        <StaffSelect v-if="session.isStore" v-model="voidStaff" placeholder="처리 직원" />
        <p>이 판매를 기록 취소할까요? 등급 실적·방문일에서 빠지고 남은 후속 연락도 취소됩니다.</p>
        <p v-if="voidError" class="field-error">{{ voidError }}</p>
      </div>
      <template #footer>
        <el-button @click="voidOpen = false">돌아가기</el-button>
        <el-button type="danger" :loading="saving" @click="saveVoid">기록 취소</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sd__back {
  align-self: flex-start;
  padding-left: 0;
}
.sd__head {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sd__title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.sd__no {
  font-size: var(--font-size-xl);
}
.sd__meta {
  display: grid;
  grid-template-columns: repeat(4, auto 1fr);
  gap: 6px 12px;
  margin: 0;
  font-size: var(--font-size-sm);
}
.sd__meta dt {
  color: var(--color-text-muted);
}
.sd__meta dd {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.sd__btns {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sd__btns .el-button + .el-button {
  margin-left: 0;
}
.sd__returned {
  color: var(--color-danger);
}
.sd__total {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  padding-top: 12px;
  font-size: var(--font-size-lg);
}
.sd__sub {
  font-size: var(--font-size-base);
  margin-bottom: 8px;
}
.sd__returns {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--font-size-sm);
}
.rt {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rt__line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.rt__name {
  display: flex;
  flex-direction: column;
}
.rt__preview {
  margin: 0;
  text-align: right;
}
@media (max-width: 767.98px) {
  .sd__meta {
    grid-template-columns: auto 1fr;
  }
}
</style>

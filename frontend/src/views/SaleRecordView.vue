<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import CustomerSearch from '@/components/CustomerSearch.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import GradeBadge from '@/components/GradeBadge.vue'
import { getCustomerForSale, getDailySummary, getStaff, createSale } from '@/api/sales'
import { defaultLineAmount, recalcLines, totals } from '@/features/sale/saleAmount'
import { formatWon, itemTypeLabels, sizeLabel, todayLabel } from '@/utils/format'
import { useIsPhone } from '@/composables/useIsPhone'

/**
 * SCR-02 판매 기록 (05 3장). 지금은 구매 모드. 환불·교환은 다음 단계에서 만든다.
 * 목표: 노트북에서 30초 안에 1건 (NFR-08a).
 */
const isPhone = useIsPhone()
const mode = ref('PURCHASE')

// 오늘 판매 수·마감 상태 (S7)
const summary = ref(null)
const staffList = ref([])
onMounted(async () => {
  ;[summary.value, staffList.value] = await Promise.all([getDailySummary(), getStaff()])
})
const dayClosed = computed(() => summary.value?.closed === true)

// ---- 입력 ----
const sellerId = ref(null)
const customer = ref(null) // null = 비회원
const lines = ref([])
const soldAt = ref(null) // null = 지금
const memo = ref('')
const errors = ref({})

const discountRate = computed(() => customer.value?.grade.discountRate ?? 0)
const hasMembers = computed(() => (customer.value?.members.length ?? 0) > 0)
const sum = computed(() => totals(lines.value))
const sellerName = computed(() => staffList.value.find((s) => s.id === sellerId.value)?.name)

let lineSeq = 0
function addLine(item) {
  lines.value.push({
    key: ++lineSeq,
    ...item,
    qty: 1,
    lineAmount: defaultLineAmount({
      listPrice: item.listPrice,
      qty: 1,
      discountRate: discountRate.value,
    }),
    edited: false,
    memberId: null,
  })
  errors.value.lines = null
}

function removeLine(key) {
  lines.value = lines.value.filter((l) => l.key !== key)
}

function changeQty(line, qty) {
  line.qty = qty
  if (!line.edited && line.listPrice != null) {
    line.lineAmount = defaultLineAmount({ ...line, discountRate: discountRate.value })
  }
}

/** 직원이 금액을 고치면 "고침" 표시, 그 뒤에는 다시 계산하지 않는다 (S4) */
function changeAmount(line, value) {
  line.lineAmount = value
  line.edited = true
}

// 고객이 바뀌면 고치지 않은 줄만 다시 계산. 할인은 판매 전 등급 기준 (BR-10)
watch(discountRate, (rate) => {
  lines.value = recalcLines(lines.value, rate)
})

async function selectCustomer(c) {
  customer.value = await getCustomerForSale(c.id)
}

function clearCustomer() {
  customer.value = null
  memo.value = ''
  lines.value.forEach((l) => (l.memberId = null))
}

function openRegister() {
  ElMessage.info('고객등록 창은 F4에서 만듭니다')
}

// ---- 판매 시각 (오늘 0시 이후, 지금보다 이른 시각) ----
const editingTime = ref(false)
function disabledHours() {
  const now = new Date().getHours()
  return Array.from({ length: 24 }, (_, h) => h).filter((h) => h > now)
}
function disabledMinutes(hour) {
  const now = new Date()
  if (hour !== now.getHours()) return []
  return Array.from({ length: 60 }, (_, m) => m).filter((m) => m > now.getMinutes())
}
const soldAtLabel = computed(() =>
  soldAt.value
    ? `오늘 ${String(soldAt.value.getHours()).padStart(2, '0')}:${String(soldAt.value.getMinutes()).padStart(2, '0')}`
    : '지금',
)

// ---- 저장 ----
let idempotencyKey = crypto.randomUUID()
const confirmOpen = ref(false)
const saving = ref(false)
const customerSearchRef = ref()
const staffSelectRef = ref()

function validate() {
  const e = {}
  if (!sellerId.value) e.seller = '판매 사원을 고르세요'
  if (!lines.value.length) e.lines = '상품을 1개 이상 담으세요'
  if (lines.value.some((l) => l.lineAmount == null || l.lineAmount < 0))
    e.lines = '결제 금액을 입력하세요'
  if (soldAt.value && soldAt.value > new Date()) e.soldAt = '지금보다 이른 시각만 고를 수 있어요'
  errors.value = e
  return Object.keys(e).length === 0
}

function requestSave() {
  if (dayClosed.value || saving.value) return
  if (validate()) confirmOpen.value = true
}

async function save() {
  if (saving.value) return
  saving.value = true
  const body = {
    sellerStaffId: sellerId.value,
    customerId: customer.value?.id,
    soldAt: soldAt.value?.toISOString(),
    memo: customer.value && memo.value.trim() ? memo.value.trim() : undefined,
    lines: lines.value.map((l) =>
      l.skuId
        ? {
            skuId: l.skuId,
            qty: l.qty,
            lineAmount: l.lineAmount,
            memberId: l.memberId ?? undefined,
          }
        : {
            productName: l.productName,
            itemType: l.itemType,
            size: l.size,
            color: l.color ?? undefined,
            target: l.target ?? undefined,
            qty: l.qty,
            lineAmount: l.lineAmount,
            memberId: l.memberId ?? undefined,
          },
    ),
  }
  try {
    const res = await createSale(body, idempotencyKey)
    ElMessage.success({
      message: customer.value
        ? `${customer.value.name}님 구매이력이 저장되었습니다 · ${res.receiptNo}`
        : `구매 기록이 저장되었습니다 · ${res.receiptNo}`,
      duration: 4000,
    })
    summary.value = { ...summary.value, saleCount: summary.value.saleCount + 1 }
    idempotencyKey = crypto.randomUUID() // 저장에 성공했을 때만 새 키 (BR-12)
    resetAll()
  } catch (err) {
    if (err?.body?.code === 'DAY_CLOSED') summary.value = { ...summary.value, closed: true }
    ElMessage.error(
      err?.body?.message ??
        '저장됐는지 확인하지 못했습니다. 다시 누르면 같은 요청으로 다시 보냅니다',
    )
  } finally {
    saving.value = false
    confirmOpen.value = false
  }
}

/** 저장 뒤 판매 사원까지 모든 칸을 비운다 (S6·S9) */
function resetAll() {
  sellerId.value = null
  customer.value = null
  lines.value = []
  soldAt.value = null
  editingTime.value = false
  memo.value = ''
  errors.value = {}
  nextTick(() => staffSelectRef.value?.focus())
}

// Ctrl+Enter = 저장, 확인 창에서 Enter = 저장
function onKeydown(e) {
  if (mode.value !== 'PURCHASE') return
  if (confirmOpen.value && e.key === 'Enter' && !e.isComposing) {
    e.preventDefault()
    save()
  } else if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    requestSave()
  }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="sale" :class="{ 'sale--phone': isPhone }">
    <header class="sale__head">
      <el-radio-group v-model="mode" class="sale__mode" aria-label="판매 종류">
        <el-radio-button value="PURCHASE">구매</el-radio-button>
        <el-radio-button value="REFUND">환불</el-radio-button>
        <el-radio-button value="EXCHANGE">교환</el-radio-button>
      </el-radio-group>
      <div v-if="summary" class="sale__today">
        <span>{{ todayLabel() }}</span>
        <span>오늘 판매 {{ summary.saleCount }}건</span>
        <span class="status-dot" :class="dayClosed ? 'is-closed' : 'is-open'">
          {{ dayClosed ? '마감됨' : '마감 전' }}
        </span>
      </div>
    </header>

    <div v-if="dayClosed" class="sale__closed" role="alert">
      오늘 판매는 마감되었습니다. 다시 열려면 본사에 요청하세요
    </div>

    <section v-if="mode !== 'PURCHASE'" class="card sale__todo">
      <h2>{{ mode === 'REFUND' ? '환불' : '교환' }}</h2>
      <p>F1 다음 단계에서 만듭니다. 회원의 구매 목록에서 상품명으로 찾아 영수증의 줄을 고릅니다.</p>
    </section>

    <div v-else class="sale__body">
      <!-- 왼쪽: 입력 (Tab 순서대로) -->
      <div class="sale__form">
        <section class="card field">
          <label class="field__label" for="seller">① 판매 사원 <em>필수</em></label>
          <StaffSelect
            id="seller"
            ref="staffSelectRef"
            v-model="sellerId"
            placeholder="판매 사원을 고르세요"
          />
          <p v-if="errors.seller" class="field__error">{{ errors.seller }}</p>
        </section>

        <section class="card field">
          <span class="field__label">② 고객</span>
          <div v-if="customer" class="customer-card">
            <strong>{{ customer.name }}</strong>
            <span class="muted">{{ customer.phoneMasked }}</span>
            <span class="muted">매장 단골 등급</span>
            <GradeBadge :grade="customer.grade.current" />
            <span v-if="customer.grade.discountRate" class="customer-card__discount">
              할인 {{ customer.grade.discountRate }}% 대상
            </span>
            <el-button link class="customer-card__change" @click="clearCustomer">바꾸기</el-button>
          </div>
          <template v-else>
            <CustomerSearch
              ref="customerSearchRef"
              @select="selectCustomer"
              @register="openRegister"
              @non-member="ElMessage.info('비회원으로 기록합니다')"
            />
            <p class="non-member">
              비회원 · 고객등록하면 등급 할인을 받을 수 있어요
              <el-button link type="primary" @click="openRegister">고객 등록</el-button>
            </p>
          </template>
        </section>

        <section class="card field">
          <span class="field__label">③ 상품 <em>필수</em></span>
          <ProductPicker @add="addLine" />
        </section>

        <section class="card field">
          <span class="field__label">④ 담은 상품</span>
          <p v-if="!lines.length" class="muted">아직 담은 상품이 없습니다</p>
          <div v-else class="lines">
            <div v-for="line in lines" :key="line.key" class="line">
              <div class="line__product">
                <strong>{{ line.productName }}</strong>
                <span class="muted">
                  {{ line.productCode ?? '직접 입력' }} · {{ itemTypeLabels[line.itemType] }} ·
                  {{ line.color ?? '색상 없음' }} /
                  {{ sizeLabel(line.size, line.itemType, line.target) }}
                </span>
                <span v-if="line.listPrice" class="muted"
                  >정가 {{ formatWon(line.listPrice) }}</span
                >
              </div>
              <el-input-number
                :model-value="line.qty"
                :min="1"
                :max="99"
                size="small"
                class="line__qty"
                aria-label="수량"
                @update:model-value="(v) => changeQty(line, v ?? 1)"
              />
              <div class="line__amount">
                <el-input-number
                  :model-value="line.lineAmount"
                  :min="0"
                  :step="1000"
                  :controls="false"
                  placeholder="결제 금액"
                  aria-label="결제 금액"
                  @update:model-value="(v) => changeAmount(line, v)"
                />
                <span class="line__won">원</span>
                <span v-if="line.edited" class="tag-edited">고침</span>
              </div>
              <el-select
                v-if="hasMembers"
                v-model="line.memberId"
                placeholder="착용자"
                clearable
                size="small"
                class="line__wearer"
              >
                <el-option
                  v-for="m in customer.members"
                  :key="m.id"
                  :label="m.label"
                  :value="m.id"
                />
              </el-select>
              <el-button link type="danger" class="line__remove" @click="removeLine(line.key)"
                >삭제</el-button
              >
            </div>
          </div>
          <p v-if="errors.lines" class="field__error">{{ errors.lines }}</p>
        </section>

        <section class="card field field--row">
          <span class="field__label">⑤ 판매 시각</span>
          <template v-if="!editingTime">
            <span>{{ soldAtLabel }}</span>
            <el-button link type="primary" @click="editingTime = true">시각 바꾸기</el-button>
          </template>
          <template v-else>
            <el-time-picker
              v-model="soldAt"
              format="HH:mm"
              placeholder="시:분"
              :disabled-hours="disabledHours"
              :disabled-minutes="disabledMinutes"
            />
            <el-button link @click="((soldAt = null), (editingTime = false))">지금으로</el-button>
          </template>
          <p v-if="errors.soldAt" class="field__error">{{ errors.soldAt }}</p>
        </section>

        <section v-if="customer" class="card field">
          <label class="field__label" for="memo">⑥ 응대 메모</label>
          <el-input
            id="memo"
            v-model="memo"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="찾던 상품, 다음에 관심 있는 것 등"
          />
        </section>
      </div>

      <!-- 오른쪽: 요약 (스크롤해도 고정) -->
      <aside class="card sale__summary" aria-label="판매 요약">
        <dl>
          <dt>판매 사원</dt>
          <dd>{{ sellerName ?? '-' }}</dd>
          <dt>고객</dt>
          <dd>
            <template v-if="customer">
              {{ customer.name }}
              <GradeBadge :grade="customer.grade.current" />
            </template>
            <template v-else>비회원</template>
          </dd>
          <dt>등급 할인</dt>
          <dd>{{ discountRate ? `${discountRate}% (안내)` : '없음' }}</dd>
          <dt>수량</dt>
          <dd>{{ sum.qty }}개</dd>
        </dl>
        <div class="sale__total">
          <span>결제 금액</span>
          <strong>{{ formatWon(sum.amount) }}</strong>
        </div>
        <el-button
          type="primary"
          size="large"
          class="sale__save"
          :disabled="dayClosed"
          :loading="saving"
          @click="requestSave"
        >
          {{ saving ? '저장 중…' : '저장' }}
          <span v-if="!isPhone" class="sale__shortcut">Ctrl+Enter</span>
        </el-button>
      </aside>
    </div>

    <!-- 저장 확인 창 -->
    <el-dialog
      v-model="confirmOpen"
      title="구매 기록을 저장할까요?"
      width="min(480px, 92vw)"
      align-center
    >
      <div v-if="!customer" class="confirm__nonmember">
        <strong>비회원 · 고객관리에 남지 않습니다</strong>
        <el-button size="small" @click="((confirmOpen = false), openRegister())"
          >고객 등록</el-button
        >
      </div>
      <dl class="confirm">
        <dt>판매 사원</dt>
        <dd>{{ sellerName }}</dd>
        <dt>고객</dt>
        <dd>{{ customer ? `${customer.name} ${customer.phoneMasked}` : '비회원' }}</dd>
        <dt>상품</dt>
        <dd>
          <div v-for="l in lines" :key="l.key">
            {{ l.productName }} {{ l.color ?? '' }}/{{ l.size }} × {{ l.qty }} ·
            {{ formatWon(l.lineAmount) }}
          </div>
        </dd>
        <dt>결제 금액</dt>
        <dd class="confirm__total">{{ formatWon(sum.amount) }}</dd>
        <dt>판매 시각</dt>
        <dd>{{ soldAtLabel }}</dd>
      </dl>
      <template #footer>
        <el-button @click="confirmOpen = false">돌아가기 (Esc)</el-button>
        <el-button type="primary" :loading="saving" @click="save">저장 (Enter)</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
}
.muted {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.sale__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.sale__today {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: var(--font-weight-medium);
}
.status-dot::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-dot.is-open {
  color: var(--color-info);
  background: var(--el-color-primary-light-9);
}
.status-dot.is-closed {
  color: var(--color-danger);
  background: var(--el-color-danger-light-9);
}
.sale__closed {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--el-color-danger-light-9);
  color: var(--color-danger);
  font-weight: var(--font-weight-semibold);
}
.sale__todo p {
  color: var(--color-text-muted);
}

.sale__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
.sale__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field--row {
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.field__label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}
.field__label em {
  margin-left: 4px;
  font-style: normal;
  font-weight: var(--font-weight-regular);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.field__error {
  margin: 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}

.customer-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
}
.customer-card__discount {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}
.customer-card__change {
  margin-left: auto;
}
.non-member {
  margin: 0;
  color: var(--color-warning);
  font-size: var(--font-size-sm);
}

.lines {
  display: flex;
  flex-direction: column;
}
.line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px 180px auto auto;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}
.line:first-child {
  border-top: 0;
}
.line__product {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.line__amount {
  display: flex;
  align-items: center;
  gap: 4px;
}
.line__amount :deep(.el-input-number) {
  width: 120px;
}
.line__amount :deep(input) {
  text-align: right;
}
.line__won {
  color: var(--color-text-muted);
}
.tag-edited {
  padding: 0 6px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-warning);
  background: var(--el-color-warning-light-9);
}
.line__wearer {
  width: 96px;
}

.sale__summary {
  position: sticky;
  top: 72px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sale__summary dl,
.confirm {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 8px 12px;
  margin: 0;
}
.sale__summary dt,
.confirm dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.sale__summary dd,
.confirm dd {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sale__summary dd {
  flex-direction: row;
  align-items: center;
  gap: 6px;
}
.sale__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.sale__total strong {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
}
.sale__save {
  width: 100%;
  height: 52px;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}
.sale__shortcut {
  margin-left: 8px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-regular);
  opacity: 0.8;
}
.confirm__nonmember {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--color-warning);
  font-size: var(--font-size-lg);
}
.confirm__total {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
}

/* 매장폰: 한 줄로 쌓고 요약은 맨 아래, [저장]은 화면 아래 고정 (R4) */
.sale--phone .sale__body {
  grid-template-columns: 1fr;
}
.sale--phone .line {
  grid-template-columns: 1fr auto;
}
.sale--phone .line__product {
  grid-column: 1 / -1;
}
.sale--phone .sale__summary {
  position: static;
  padding-bottom: 80px;
}
.sale--phone .sale__save {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: calc(64px + env(safe-area-inset-bottom));
  width: auto;
  z-index: 11;
  box-shadow: 0 4px 12px rgb(15 23 42 / 0.15);
}
</style>

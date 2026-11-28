<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StaffSelect from '@/components/StaffSelect.vue'
import LoadError from '@/components/LoadError.vue'
import CustomerSearch from '@/components/CustomerSearch.vue'
import GradeBadge from '@/components/GradeBadge.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import SaleLines from '@/components/sale/SaleLines.vue'
import { correctSale, getCustomerForSale, getSale } from '@/api/sales'
import { useSaleChangeRules } from '@/composables/useSaleChangeRules'
import { dateTimeLabel } from '@/features/customer/customerDetail'
import { leftAmount, leftQty } from '@/features/sale/returnAmount'
import { wearerIdOf } from '@/features/sale/saleChange'
import { useIsPhone } from '@/composables/useIsPhone'
import { useLoadError } from '@/composables/useLoadError'
import { useSaleLines } from '@/composables/useSaleLines'
import { useSaleShortcuts } from '@/composables/useSaleShortcuts'
import { useSessionStore } from '@/stores/session'
import { apiMessage } from '@/utils/apiMessage'
import { formatWon } from '@/utils/format'

/**
 * 판매 기록의 정정 모드 `/sales/{id}/edit` (05 SCR-02, SAL-07, S12, BR-16).
 * 영수증 상세의 [정정]에서 들어온다. 남은 줄(반품되지 않은 수량)만 불러오고 결제 금액은 원래 값 그대로다.
 * 판매 사원·판매 시각은 원래 값으로 잠그고, 매장 계정은 처리 직원을 고른다. 응대 메모는 정정 판매로 옮겨진다.
 * [정정 저장] → `PUT /sales/{id}` → 원 판매는 기록 취소, 새 영수증 상세로 이동.
 */
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const isPhone = useIsPhone()

const sale = ref(null)
const notFound = ref(false)
const customer = ref(null)
const discountRate = computed(() => customer.value?.grade.discountRate ?? 0)
const members = computed(() => customer.value?.members ?? [])
const {
  lines,
  sum,
  invalid,
  addLine,
  setLines,
  removeLine,
  changeQty,
  changeAmount,
  changeWearer,
  clearWearers,
  toBody,
} = useSaleLines(discountRate)

const { canChange, isOwnStore, refreshClosed } = useSaleChangeRules()

/** 고칠 수 있는지: 매장 계정은 판매한 날·마감 전, 본사는 언제나. 기록 취소·전부 반품이면 안 됨 */
const blockedReason = computed(() => {
  const s = sale.value
  if (!s) return null
  if (s.status === 'VOIDED') return '기록 취소된 판매는 정정할 수 없습니다.'
  if (!s.lines.some((l) => leftQty(l) > 0)) return '전부 반품된 판매는 정정할 수 없습니다.'
  if (session.isStore && !isOwnStore(s))
    return `다른 매장(${s.store.name}) 판매는 그 매장에서 고칩니다.`
  if (!canChange(s)) return '판매한 날이 지났거나 마감된 판매는 본사가 고칩니다.'
  return null
})

// 고객을 못 읽은 채로 열면 저장할 때 고객 연결이 지워지므로, 다 읽은 뒤에만 화면을 연다
const { error, retrying, run } = useLoadError({ ignore: (e) => e?.status === 404 })

async function fetchSale() {
  const [s] = await Promise.all([getSale(Number(route.params.id)), refreshClosed()])
  customer.value = s.customerId ? await getCustomerForSale(s.customerId) : null
  setLines(
    s.lines
      .filter((l) => leftQty(l) > 0)
      .map((l) => ({
        skuId: l.skuId,
        productCode: l.productCode,
        productName: l.productName,
        itemType: l.itemType,
        color: l.color,
        size: l.size,
        target: l.target ?? null,
        listPrice: l.listPrice,
        qty: leftQty(l),
        lineAmount: leftAmount(l),
        memberId: wearerIdOf(l, customer.value?.members),
      })),
  )
  sale.value = s
}

async function load() {
  notFound.value = false
  sale.value = null
  try {
    await run(fetchSale)
  } catch {
    notFound.value = true
  }
}
watch(() => route.params.id, load, { immediate: true })

async function selectCustomer(c) {
  customer.value = await getCustomerForSale(c.id)
  clearWearers()
}
function toNonMember() {
  customer.value = null
  clearWearers()
}

// ---- 저장 ----
let idempotencyKey = crypto.randomUUID()
const staffId = ref(null)
const errors = ref({})
const confirmOpen = ref(false)
const saving = ref(false)

function requestSave() {
  if (blockedReason.value || saving.value) return
  const e = {}
  if (session.isStore && !staffId.value) e.staff = '처리 직원을 고르세요'
  if (!lines.value.length) e.lines = '상품을 1개 이상 담으세요. 모두 빼려면 [기록 취소]를 쓰세요'
  else if (invalid.value) e.lines = '결제 금액을 입력하세요'
  errors.value = e
  if (!Object.keys(e).length) confirmOpen.value = true
}

async function save() {
  if (saving.value) return
  saving.value = true
  const body = {
    staffId: session.isStore ? staffId.value : undefined,
    customerId: customer.value?.id,
    lines: toBody(),
  }
  try {
    const res = await correctSale(sale.value.id, body, idempotencyKey)
    idempotencyKey = crypto.randomUUID()
    ElMessage.success({
      message: `정정했습니다 · 새 영수증 ${res.receiptNo}`,
      duration: 4000,
    })
    router.replace(`/sales/${res.id}`)
  } catch (err) {
    ElMessage.error(
      apiMessage(err, '저장됐는지 확인하지 못했습니다. 다시 누르면 같은 요청으로 다시 보냅니다'),
    )
    if (err?.status === 409) load()
  } finally {
    saving.value = false
    confirmOpen.value = false
  }
}

useSaleShortcuts({ confirmOpen, onSave: save, onRequest: requestSave })
</script>

<template>
  <!-- 판매 기록 화면과 같은 폭 (담은 상품 표가 옆으로 밀리지 않게) -->
  <div class="page sc">
    <el-button link class="sc__back" @click="router.back()">‹ 영수증</el-button>

    <div v-if="notFound" class="card empty">
      찾을 수 없습니다.
      <el-button link type="primary" @click="router.push('/sales')">판매 내역으로</el-button>
    </div>
    <LoadError v-else-if="error && !sale" :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-else-if="!sale" animated :rows="6" class="card" />

    <div v-else-if="blockedReason" class="card empty">
      {{ blockedReason }}
      <el-button link type="primary" @click="router.replace(`/sales/${sale.id}`)"
        >영수증으로</el-button
      >
    </div>

    <template v-else>
      <header class="sc__head">
        <h1 class="page__title">정정 · {{ sale.receiptNo }}</h1>
        <span class="sc__tag">정정</span>
      </header>

      <div class="sp" :class="{ 'sp--phone': isPhone }">
        <div class="sp__main">
          <div class="ftable">
            <div class="ft-label">판매 시각</div>
            <div class="ft-value">
              {{ dateTimeLabel(sale.soldAt, new Date(), true) }}
              <span class="muted">원래 시각 그대로</span>
            </div>
            <div class="ft-label">판매 사원</div>
            <div class="ft-value">
              {{ sale.sellerName }}
              <span class="muted">원래 직원 그대로</span>
            </div>
            <template v-if="session.isStore">
              <label class="ft-label is-required" for="correction-staff">처리 직원</label>
              <div class="ft-value ft-wide">
                <StaffSelect
                  id="correction-staff"
                  v-model="staffId"
                  placeholder="정정을 처리한 직원"
                />
                <p v-if="errors.staff" class="field-error">{{ errors.staff }}</p>
              </div>
            </template>
            <div class="ft-label">응대 메모</div>
            <div class="ft-value ft-wide ft-muted">응대 메모는 정정 판매로 옮겨집니다</div>
          </div>

          <p class="sp__note">
            반품되지 않은 줄만 불러왔고, 결제 금액은 원래 값입니다. 수량을 바꾸면 결제 금액도 직접
            고쳐 주세요.
          </p>

          <section v-if="isPhone" class="sc__card">
            <ProductPicker @add="addLine" />
          </section>
          <ProductPicker v-else layout="row" @add="addLine" />
          <SaleLines
            :lines="lines"
            :members="members"
            :phone="isPhone"
            kind-label="정정"
            @qty="changeQty"
            @amount="changeAmount"
            @wearer="changeWearer"
            @remove="removeLine"
          />
          <p v-if="errors.lines" class="field-error">{{ errors.lines }}</p>
        </div>

        <aside class="sp__side" aria-label="고객 정보와 정정 내역">
          <div class="ftable ftable--2">
            <div class="ft-caption">고객 정보</div>
            <template v-if="customer">
              <div class="ft-label">고객명</div>
              <div class="ft-value">
                <div class="ft-inline">
                  <strong>{{ customer.name }}</strong>
                  <GradeBadge :grade="customer.grade.current" />
                  <el-button link size="small" @click="toNonMember">비회원으로</el-button>
                </div>
              </div>
              <div class="ft-label">연락처</div>
              <div class="ft-value">{{ customer.phoneMasked }}</div>
            </template>
            <div v-else class="ft-value ft-full">
              <span class="muted">비회원 · 고객을 고르면 연결을 고칩니다</span>
            </div>
            <div class="ft-value ft-full">
              <CustomerSearch members-only @select="selectCustomer" />
            </div>
          </div>

          <div class="ftable ftable--2">
            <div class="ft-caption">정정 내역</div>
            <div class="ft-label">원 영수증</div>
            <div class="ft-value">{{ sale.receiptNo }}</div>
            <div class="ft-label">원래 합계</div>
            <div class="ft-value">
              {{ formatWon(sale.lines.reduce((n, l) => n + leftAmount(l), 0)) }}
            </div>
            <div class="ft-label">수량</div>
            <div class="ft-value">{{ sum.qty }}개</div>
            <div class="ft-label">결제 금액</div>
            <div class="ft-value sp__sum">{{ formatWon(sum.amount) }}</div>
          </div>
          <el-button
            type="primary"
            size="large"
            class="sp__save"
            :loading="saving"
            @click="requestSave"
          >
            {{ saving ? '저장 중…' : '정정 저장' }}
            <span v-if="!isPhone" class="sp__shortcut">Ctrl+Enter</span>
          </el-button>
        </aside>
      </div>
    </template>

    <el-dialog v-model="confirmOpen" title="정정할까요?" width="min(480px, 92vw)" align-center>
      <p class="sp__confirm-warn">원 판매는 기록 취소되고 새 영수증이 만들어집니다</p>
      <dl v-if="sale" class="sp__confirm">
        <dt>판매 사원</dt>
        <dd>{{ sale.sellerName }}</dd>
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
        <dd class="sp__confirm-total">{{ formatWon(sum.amount) }}</dd>
      </dl>
      <template #footer>
        <el-button @click="confirmOpen = false">돌아가기 (Esc)</el-button>
        <el-button type="primary" :loading="saving" @click="save">정정 저장 (Enter)</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped src="@/components/sale/salePanel.css"></style>
<style scoped>
.sc {
  max-width: 1280px;
}
.sc__back {
  align-self: flex-start;
  padding-left: 0;
}
.sc__head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sc__tag {
  padding: 2px 10px;
  border-radius: 999px;
  color: var(--color-info);
  background: var(--el-color-primary-light-9);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}
.sc__card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
}
</style>

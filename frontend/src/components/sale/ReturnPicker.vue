<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getPurchases } from '@/api/customers'
import { getSale, isOwnStoreSale } from '@/api/sales'
import { dateTimeLabel } from '@/features/customer/customerDetail'
import { leftQty, refundAmount } from '@/features/sale/returnAmount'
import { formatWon } from '@/utils/format'

/**
 * 돌려받을 상품 고르기 (05 SCR-02 환불·교환 모드, SAL-03, S10·S11).
 * 회원의 구매 목록을 상품명·품번으로 찾고(`keyword`), 영수증별로 묶어 줄마다 반품 수량을 넣는다.
 * 한 번에 한 영수증의 줄만 고른다. 영수증 상세에서 들어오면(saleId) 그 영수증 하나만 보여준다.
 *
 * v-model: `{ sale, qty }` (sale = 고른 영수증, qty = 줄마다 반품 수량) 또는 null
 */
const props = defineProps({
  customerId: { type: Number, default: null },
  saleId: { type: Number, default: null },
})
const selection = defineModel({ type: Object, default: null })

const keyword = ref('')
const searched = ref('')
const receipts = ref(null)
const qty = ref({}) // `${saleId}-${lineId}` → 수량
const rev = ref(0) // 고르기를 물리면 입력 칸을 다시 그려 원래 값으로 되돌린다

/** 영수증 상세(4.12) → 구매이력 항목 모양 */
const fromSale = (s) => ({
  saleId: s.id,
  receiptNo: s.receiptNo,
  soldAt: s.soldAt,
  storeId: s.store.id,
  storeName: s.store.name,
  status: s.status,
  lines: s.lines,
})

async function load() {
  receipts.value = null
  searched.value = keyword.value.trim()
  if (props.saleId) {
    const s = await getSale(props.saleId)
    receipts.value = s.status === 'VOIDED' ? [] : [fromSale(s)]
  } else if (props.customerId) {
    receipts.value = (await getPurchases(props.customerId, searched.value)).content
  } else {
    receipts.value = []
  }
}

/** 저장한 뒤처럼 처음부터 다시: 고른 수량을 비우고 목록을 다시 불러온다 */
async function reload() {
  qty.value = {}
  selection.value = null
  await load()
}
watch(() => [props.customerId, props.saleId], reload, { immediate: true })

/** 다른 매장 영수증은 목록에는 보이되 환불·교환은 그 매장이 한다 (보기만) */
const isOther = (r) => !isOwnStoreSale({ store: { id: r.storeId } })
const keyOf = (r, l) => `${r.saleId}-${l.lineId}`
const chosenSaleId = computed(() => selection.value?.sale.saleId ?? null)

function emitSelection(r) {
  const picked = Object.fromEntries(
    r.lines.map((l) => [l.lineId, qty.value[keyOf(r, l)] ?? 0]).filter(([, q]) => q > 0),
  )
  selection.value = Object.keys(picked).length ? { sale: r, qty: picked } : null
}

async function changeQty(r, l, value) {
  const v = value ?? 0
  // 다른 영수증의 줄을 고르면 앞에서 고른 줄을 비울지 묻는다 (S11)
  if (v > 0 && chosenSaleId.value && chosenSaleId.value !== r.saleId) {
    try {
      await ElMessageBox.confirm(
        `한 번에 한 영수증만 고를 수 있습니다. ${selection.value.sale.receiptNo}에서 고른 줄을 비우고 이 영수증을 고를까요?`,
        '다른 영수증',
        { confirmButtonText: '비우고 고르기', cancelButtonText: '돌아가기', type: 'warning' },
      )
    } catch {
      rev.value++
      return
    }
    qty.value = {}
  }
  qty.value = { ...qty.value, [keyOf(r, l)]: v }
  emitSelection(r)
}

const lineRefund = (r, l) => refundAmount(l, qty.value[keyOf(r, l)] ?? 0)

defineExpose({ reload })
</script>

<template>
  <div class="rp">
    <form v-if="!saleId" class="rp__search" @submit.prevent="load">
      <el-input
        v-model="keyword"
        placeholder="상품명·품번으로 찾기 (비우면 전체)"
        clearable
        :disabled="!customerId"
        @clear="load"
      />
      <el-button native-type="submit" :disabled="!customerId">찾기</el-button>
    </form>

    <p v-if="!customerId && !saleId" class="rp__empty">고객을 먼저 고르세요</p>
    <el-skeleton v-else-if="!receipts" animated :rows="3" />
    <p v-else-if="!receipts.length" class="rp__empty">
      {{
        searched
          ? `이 고객의 '${searched}' 구매가 없습니다`
          : saleId
            ? '돌려받을 수 있는 줄이 없습니다'
            : '이 고객의 구매가 없습니다'
      }}
    </p>

    <article
      v-for="r in receipts"
      v-else
      :key="`${r.saleId}-${rev}`"
      class="rp__receipt"
      :class="{ 'is-chosen': chosenSaleId === r.saleId }"
    >
      <header class="rp__head">
        <strong>{{ r.receiptNo }}</strong>
        <span class="muted">{{ dateTimeLabel(r.soldAt) }} · {{ r.storeName }}</span>
        <el-tag v-if="chosenSaleId === r.saleId" size="small">고름</el-tag>
        <span v-if="isOther(r)" class="rp__other" role="note">
          다른 매장 판매입니다. {{ r.storeName }}에서 환불·교환해 주세요 (보기만)
        </span>
      </header>
      <div class="rp__row rp__row--head" role="row">
        <span>상품</span>
        <span class="num">판매</span>
        <span class="num">남은</span>
        <span class="num">결제 금액</span>
        <span class="num">반품 수량</span>
        <span class="num">환불 예상</span>
      </div>
      <div
        v-for="l in r.lines"
        :key="l.lineId"
        class="rp__row"
        :class="{ 'is-done': !leftQty(l) }"
        role="row"
      >
        <span class="rp__name">
          <span :class="{ strike: !leftQty(l) }">{{ l.productName }}</span>
          <span class="muted"
            >{{ l.productCode ?? '직접 입력' }} · {{ l.color ?? '-' }} / {{ l.size }}</span
          >
        </span>
        <span class="num" data-label="판매">{{ l.qty }}개</span>
        <span class="num" data-label="남은">{{ leftQty(l) }}개</span>
        <span class="num" data-label="결제 금액">{{ formatWon(l.lineAmount) }}</span>
        <span class="num rp__qtycell" data-label="반품 수량">
          <el-input-number
            :model-value="qty[keyOf(r, l)] ?? 0"
            :min="0"
            :max="leftQty(l)"
            :disabled="!leftQty(l) || isOther(r)"
            size="small"
            class="rp__qty"
            :aria-label="`${l.productName} 반품 수량`"
            @update:model-value="(v) => changeQty(r, l, v)"
          />
        </span>
        <span class="num" data-label="환불 예상">
          {{ qty[keyOf(r, l)] ? formatWon(lineRefund(r, l)) : '-' }}
        </span>
      </div>
    </article>
  </div>
</template>

<style scoped>
.rp {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rp__search {
  display: flex;
  gap: 8px;
}
.rp__empty {
  margin: 0;
  padding: 20px 12px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 6px;
}
.muted {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.rp__receipt {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}
.rp__receipt.is-chosen {
  border-color: var(--color-primary);
}
.rp__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-subtle);
}
.rp__other {
  color: var(--color-warning);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}
.rp__row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 56px 56px 100px 112px 100px;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-top: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
}
.rp__row--head {
  color: var(--color-text-muted);
  font-weight: var(--font-weight-medium);
}
.rp__row.is-done {
  color: var(--color-text-muted);
}
.rp__row .num {
  text-align: right;
  justify-self: end;
}
.rp__name {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.rp__qty {
  width: 100px;
}

/* 폰: 줄마다 이름 위, 값은 라벨과 함께 두 칸씩 */
@media (max-width: 767.98px) {
  .rp__row--head {
    display: none;
  }
  .rp__row {
    grid-template-columns: 1fr 1fr;
    padding: 10px 12px;
  }
  .rp__name,
  .rp__qtycell {
    grid-column: 1 / -1;
  }
  .rp__row .num {
    justify-self: stretch;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 6px;
  }
  .rp__row .num::before {
    content: attr(data-label);
    color: var(--color-text-muted);
  }
}
</style>

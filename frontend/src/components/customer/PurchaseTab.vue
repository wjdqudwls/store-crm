<script setup>
import { onMounted, ref } from 'vue'
import { getPurchases } from '@/api/customers'
import { dateTimeLabel } from '@/features/customer/customerDetail'
import { formatWon } from '@/utils/format'

/**
 * 구매이력 (05 SCR-06, CUS-03). 영수증 카드 → 줄. 반품된 수량은 줄 긋기와 `1개 반품`.
 * 영수증 상세(SCR-03, F6)는 아직 없어 영수증 번호는 글자로만 보인다.
 */
const props = defineProps({ customerId: { type: Number, required: true } })

const keyword = ref('')
const list = ref(null)

async function load() {
  list.value = null
  list.value = (await getPurchases(props.customerId, keyword.value)).content
}
onMounted(load)

const statusLabels = {
  PARTIAL_RETURNED: '일부 반품',
  RETURNED: '전체 반품',
}
</script>

<template>
  <div class="ptab">
    <form class="ptab__search" @submit.prevent="load">
      <el-input v-model="keyword" placeholder="상품명·품번으로 거르기" clearable @clear="load" />
      <el-button native-type="submit">찾기</el-button>
    </form>

    <el-skeleton v-if="!list" animated :rows="4" />
    <p v-else-if="!list.length" class="ptab__empty">
      {{ keyword ? `'${keyword}' 상품을 산 기록이 없습니다.` : '구매 기록이 없습니다.' }}
    </p>

    <article v-for="p in list" v-else :key="p.saleId" class="receipt">
      <header class="receipt__head">
        <span class="receipt__date">{{ dateTimeLabel(p.soldAt) }}</span>
        <span class="receipt__no">{{ p.receiptNo }}</span>
        <span class="receipt__store">{{ p.storeName }}</span>
        <el-tag v-if="statusLabels[p.status]" size="small" type="warning">
          {{ statusLabels[p.status] }}
        </el-tag>
        <strong class="receipt__total">{{ formatWon(p.totalAmount) }}</strong>
      </header>
      <ul class="receipt__lines">
        <li
          v-for="l in p.lines"
          :key="l.lineId"
          class="pline"
          :class="{ 'pline--returned': l.returnedQty === l.qty }"
        >
          <span class="pline__name">
            {{ l.productName }}
            <span class="pline__opt">{{ l.color ?? '' }} / {{ l.size }}</span>
            <span v-if="l.wearerLabel" class="pline__wearer">착용 {{ l.wearerLabel }}</span>
          </span>
          <span class="pline__qty">{{ l.qty }}개</span>
          <span class="pline__amount">{{ formatWon(l.lineAmount) }}</span>
          <span v-if="l.returnedQty" class="pline__returned">{{ l.returnedQty }}개 반품</span>
        </li>
      </ul>
    </article>
  </div>
</template>

<style scoped>
.ptab {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ptab__search {
  display: flex;
  gap: 8px;
}
.ptab__empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-muted);
}
.receipt {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}
.receipt__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  padding: 10px 12px;
  background: var(--color-subtle);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}
.receipt__no {
  color: var(--color-text-muted);
}
.receipt__total {
  margin-left: auto;
  font-size: var(--font-size-base);
}
.receipt__lines {
  list-style: none;
  margin: 0;
  padding: 0;
}
.pline {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 4px 12px;
  padding: 10px 12px;
  border-top: 1px solid var(--color-border);
  font-variant-numeric: tabular-nums;
}
.pline__opt {
  margin-left: 6px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.pline__wearer {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
}
.pline__qty {
  color: var(--color-text-muted);
}
.pline__returned {
  grid-column: 1 / -1;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
.pline--returned .pline__name,
.pline--returned .pline__qty,
.pline--returned .pline__amount {
  text-decoration: line-through;
  color: var(--color-text-muted);
}
</style>

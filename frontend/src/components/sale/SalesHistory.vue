<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSales } from '@/api/sales'
import { dateTimeLabel } from '@/features/customer/customerDetail'
import { formatWon } from '@/utils/format'
import { useIsPhone } from '@/composables/useIsPhone'

/**
 * 판매 내역 표 (05 SCR-03). 기간 빠른 버튼(오늘·어제·7일·이번 달) + 표.
 * 매장 계정의 `/sales`와 본사 매장 상세의 판매 내역 탭이 같이 쓴다 (storeId).
 */
const props = defineProps({ storeId: { type: Number, default: null } })
const router = useRouter()
const isPhone = useIsPhone()

const pad = (n) => String(n).padStart(2, '0')
const key = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
function rangeOf(kind) {
  const now = new Date()
  const from = new Date(now)
  if (kind === 'yesterday') from.setDate(now.getDate() - 1)
  if (kind === 'week') from.setDate(now.getDate() - 6)
  if (kind === 'month') from.setDate(1)
  if (kind === 'all') return [null, null]
  const to = kind === 'yesterday' ? from : now
  return [key(from), key(to)]
}

const period = ref('today')
const list = ref(null)
async function load() {
  list.value = null
  const [from, to] = rangeOf(period.value)
  list.value = (await getSales({ from, to, storeId: props.storeId })).content
}
onMounted(load)
defineExpose({ load })

const total = computed(() =>
  (list.value ?? []).filter((s) => s.status !== 'VOIDED').reduce((a, s) => a + s.totalAmount, 0),
)

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
const kindLabels = { EXCHANGE: '교환', CORRECTION: '정정' }

function open(s) {
  router.push(`/sales/${s.id}`)
}
const rowClass = ({ row }) =>
  row.status === 'VOIDED' ? 'sales__voided clickable-row' : 'clickable-row'
</script>

<template>
  <div class="sales">
    <div class="ftable">
      <div class="ft-label">판매 기간</div>
      <div class="ft-value">
        <el-radio-group v-model="period" size="small" @change="load">
          <el-radio-button value="today">오늘</el-radio-button>
          <el-radio-button value="yesterday">어제</el-radio-button>
          <el-radio-button value="week">7일</el-radio-button>
          <el-radio-button value="month">이번 달</el-radio-button>
          <el-radio-button value="all">전체</el-radio-button>
        </el-radio-group>
      </div>
      <div class="ft-label">합계</div>
      <div class="ft-value">
        <span v-if="list">
          {{ list.length }}건 · <strong>{{ formatWon(total) }}</strong>
          <span class="muted"> (기록 취소 제외)</span>
        </span>
      </div>
    </div>

    <el-skeleton v-if="!list" animated :rows="5" />
    <p v-else-if="!list.length" class="empty">이 기간의 판매가 없습니다.</p>

    <!-- 폰: 카드 목록 -->
    <ul v-else-if="isPhone" class="sales__cards">
      <li v-for="s in list" :key="s.id" :class="{ sales__voided: s.status === 'VOIDED' }">
        <button type="button" class="sales__card" @click="open(s)">
          <span>
            <strong>{{ s.customerName ?? '비회원' }}</strong>
            <span class="muted"> · {{ dateTimeLabel(s.soldAt) }}</span>
          </span>
          <span class="muted">{{ s.receiptNo }} · {{ s.sellerName }}</span>
          <span class="sales__card-foot">
            <el-tag size="small" :type="statusType[s.status]">{{ statusLabels[s.status] }}</el-tag>
            <strong>{{ formatWon(s.totalAmount) }}</strong>
          </span>
        </button>
      </li>
    </ul>

    <!-- PC: 표 -->
    <el-table v-else :data="list" :row-class-name="rowClass" @row-click="open">
      <el-table-column label="시각" width="110">
        <template #default="{ row }">{{ dateTimeLabel(row.soldAt) }}</template>
      </el-table-column>
      <el-table-column label="영수증 번호" min-width="180">
        <template #default="{ row }">
          {{ row.receiptNo }}
          <el-tag v-if="kindLabels[row.kind]" size="small" effect="plain">
            {{ kindLabels[row.kind] }}
          </el-tag>
          <div v-if="row.originReceiptNo" class="muted">원 {{ row.originReceiptNo }}</div>
        </template>
      </el-table-column>
      <el-table-column label="고객" min-width="90">
        <template #default="{ row }">{{ row.customerName ?? '비회원' }}</template>
      </el-table-column>
      <el-table-column prop="sellerName" label="판매 사원" width="100" />
      <el-table-column prop="totalQty" label="수량" width="70" align="right" />
      <el-table-column label="결제 금액" width="130" align="right">
        <template #default="{ row }">{{ formatWon(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="상태" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType[row.status]">{{
            statusLabels[row.status]
          }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.sales {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sales__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.sales :deep(.sales__voided) {
  color: var(--color-text-muted);
  background: var(--color-subtle);
}
.sales__cards {
  list-style: none;
  margin: 0;
  padding: 0;
}
.sales__cards li + li {
  border-top: 1px solid var(--color-border);
}
.sales__card {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.sales__card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>

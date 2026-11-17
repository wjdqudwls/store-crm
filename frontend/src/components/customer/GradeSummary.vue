<script setup>
import { computed, ref } from 'vue'
import GradeBadge from '@/components/GradeBadge.vue'
import { getGradeHistory } from '@/api/customers'
import {
  dateLabel,
  dateTimeLabel,
  daysAgoLabel,
  gradeNotes,
  gradeReasonLabels,
} from '@/features/customer/customerDetail'
import { formatWon, gradeLabel } from '@/utils/format'

/**
 * 요약: 등급 근거와 방문 (05 SCR-06 D3, GRD-03·05·06).
 * 이번 시즌 실적 기준 등급과 확정(6월이면 임시) 등급 중 높은 것을 적용한다.
 */
const props = defineProps({ customer: { type: Object, required: true } })

const g = computed(() => props.customer.grade)
const s = computed(() => props.customer.stats)
const provisional = computed(() => g.value.confirmedStatus === 'PROVISIONAL')
const notes = computed(() => gradeNotes(g.value))

const history = ref(null)
const historyOpen = ref(false)
async function toggleHistory() {
  historyOpen.value = !historyOpen.value
  if (historyOpen.value && !history.value) {
    history.value = (await getGradeHistory(props.customer.id)).content
  }
}
</script>

<template>
  <section class="gsum" aria-label="등급 근거와 방문">
    <div class="gsum__current">
      <span class="gsum__label">매장 단골 등급</span>
      <GradeBadge :grade="g.current" :provisional="provisional" />
      <span>적용 중</span>
      <span v-if="g.discountRate" class="gsum__discount">할인 {{ g.discountRate }}% 대상</span>
    </div>

    <div class="gsum__box">
      <h3 class="gsum__box-title">이번 시즌 {{ g.season }} 실적</h3>
      <dl class="gsum__score">
        <dt>티켓 {{ g.seasonTicketCount }}건</dt>
        <dd>{{ g.ticketScore }}점</dd>
        <dt>금액 {{ formatWon(g.seasonNetAmount) }}</dt>
        <dd>{{ g.amountScore }}점</dd>
        <dt class="gsum__total">합계 {{ g.totalScore }}점</dt>
        <dd class="gsum__total">실적 기준 <GradeBadge :grade="g.computed" /></dd>
      </dl>
    </div>

    <div class="gsum__box gsum__box--muted">
      <h3 class="gsum__box-title">
        {{ provisional ? '임시 등급' : '확정 등급' }} ({{ g.confirmedBasis.season }} 시즌)
        <template v-if="provisional"> · 7/1 확정</template>
      </h3>
      <p class="gsum__confirmed">
        <GradeBadge :grade="g.confirmed" :provisional="provisional" />
        티켓 {{ g.confirmedBasis.ticketCount }}건 · {{ formatWon(g.confirmedBasis.netAmount) }} ·
        {{ g.confirmedBasis.totalScore }}점
      </p>
    </div>

    <p v-for="n in notes" :key="n" class="gsum__note">{{ n }}</p>

    <el-button text type="primary" class="gsum__history-btn" @click="toggleHistory">
      등급 이력 보기 {{ historyOpen ? '▴' : '▾' }}
    </el-button>
    <div v-if="historyOpen" class="gsum__history">
      <el-skeleton v-if="!history" animated :rows="2" />
      <p v-else-if="!history.length" class="gsum__muted">등급 이력이 없습니다.</p>
      <table v-else>
        <thead>
          <tr>
            <th>시각</th>
            <th>등급</th>
            <th>사유</th>
            <th>시즌·점수</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in history" :key="h.id">
            <td>{{ dateTimeLabel(h.createdAt) }}</td>
            <td>{{ gradeLabel(h.fromGrade) }} → {{ gradeLabel(h.toGrade) }}</td>
            <td>{{ gradeReasonLabels[h.reason] }}</td>
            <td>{{ h.season }} · {{ h.totalScore }}점</td>
          </tr>
        </tbody>
      </table>
    </div>

    <dl class="gsum__visit">
      <dt>가입</dt>
      <dd>
        {{ customer.homeStore.name }}
        {{ customer.createdAt ? dateLabel(customer.createdAt) : '' }}
      </dd>
      <dt>첫 방문</dt>
      <dd>{{ s.firstVisitAt ? dateLabel(s.firstVisitAt) : '구매 없음' }}</dd>
      <dt>마지막 방문</dt>
      <dd>
        <template v-if="s.lastVisitAt">
          {{ dateLabel(s.lastVisitAt) }} ({{ daysAgoLabel(s.lastVisitAt) }})
        </template>
        <template v-else>구매 없음</template>
      </dd>
      <dt>이번 시즌</dt>
      <dd>{{ s.seasonQty }}개</dd>
      <dt>누적</dt>
      <dd>{{ formatWon(s.lifetimeAmount) }}</dd>
    </dl>
  </section>
</template>

<style scoped>
.gsum {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.gsum__current {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.gsum__label {
  font-weight: var(--font-weight-semibold);
}
.gsum__discount {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.gsum__box {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
}
.gsum__box--muted {
  background: var(--color-subtle);
}
.gsum__box-title {
  margin: 0 0 8px;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
}
.gsum__score {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 12px;
  margin: 0;
  font-variant-numeric: tabular-nums;
}
.gsum__score dd {
  margin: 0;
  text-align: right;
}
.gsum__total {
  padding-top: 6px;
  border-top: 1px dashed var(--color-border);
  font-weight: var(--font-weight-semibold);
  display: flex;
  align-items: center;
  gap: 6px;
}
dd.gsum__total {
  justify-content: flex-end;
}
.gsum__confirmed {
  margin: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: var(--font-size-sm);
}
.gsum__note {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
.gsum__history-btn {
  align-self: flex-start;
  padding-left: 0;
}
.gsum__history table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}
.gsum__history th,
.gsum__history td {
  text-align: left;
  padding: 6px 4px;
  border-bottom: 1px solid var(--color-border);
}
.gsum__history th {
  color: var(--color-text-muted);
  font-weight: var(--font-weight-regular);
}
.gsum__muted {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.gsum__visit {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 12px;
  margin: 0;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}
.gsum__visit dt {
  color: var(--color-text-muted);
}
.gsum__visit dd {
  margin: 0;
}
</style>

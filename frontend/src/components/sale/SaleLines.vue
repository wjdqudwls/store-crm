<script setup>
import { computed } from 'vue'
import { totals } from '@/features/sale/saleAmount'
import { formatWon, itemTypeLabels, sizeLabel } from '@/utils/format'

/**
 * 담은 상품 표 (05 SCR-02, 1.8). PC는 기존 POS 표(구분 · 품번 · 상품명 · 색상 · 사이즈 · 정가 · 수량 ·
 * 결제 금액 · 착용자 · 삭제 + 합계 줄), 폰은 줄 카드. 구매·교환(새 상품)·정정이 같이 쓴다.
 * 값은 부모(useSaleLines)가 바꾼다.
 */
const props = defineProps({
  lines: { type: Array, required: true },
  /** 착용자로 고를 가족 구성원. 비어 있으면 착용자 칸을 숨긴다 (BR-13) */
  members: { type: Array, default: () => [] },
  phone: { type: Boolean, default: false },
  /** 구분 칸 글자 */
  kindLabel: { type: String, default: '구매' },
  emptyText: {
    type: String,
    default: '품번이나 상품명으로 찾아 [담기]를 누르면 여기에 나옵니다',
  },
})
const emit = defineEmits(['qty', 'amount', 'wearer', 'remove'])

const sum = computed(() => totals(props.lines))
const hasMembers = computed(() => props.members.length > 0)
</script>

<template>
  <!-- 폰: 줄 카드 -->
  <div v-if="phone" class="lines">
    <p v-if="!lines.length" class="muted">아직 담은 상품이 없습니다</p>
    <div v-for="line in lines" :key="line.key" class="line">
      <div class="line__product">
        <strong>{{ line.productName }}</strong>
        <span class="muted">
          {{ line.productCode ?? '직접 입력' }} · {{ itemTypeLabels[line.itemType] }} ·
          {{ line.color ?? '색상 없음' }} /
          {{ sizeLabel(line.size, line.itemType, line.target) }}
        </span>
        <span v-if="line.listPrice" class="muted">정가 {{ formatWon(line.listPrice) }}</span>
      </div>
      <el-input-number
        :model-value="line.qty"
        :min="1"
        :max="99"
        size="small"
        class="line__qty"
        aria-label="수량"
        @update:model-value="(v) => emit('qty', line, v ?? 1)"
      />
      <div class="line__amount">
        <el-input-number
          :model-value="line.lineAmount"
          :min="0"
          :step="1000"
          :controls="false"
          placeholder="결제 금액"
          aria-label="결제 금액"
          @update:model-value="(v) => emit('amount', line, v)"
        />
        <span class="line__won">원</span>
        <span v-if="line.touched" class="tag-edited">고침</span>
      </div>
      <el-select
        v-if="hasMembers"
        :model-value="line.memberId"
        placeholder="착용자"
        clearable
        size="small"
        class="line__wearer"
        @update:model-value="(v) => emit('wearer', line, v)"
      >
        <el-option v-for="m in members" :key="m.id" :label="m.label" :value="m.id" />
      </el-select>
      <el-button link type="danger" class="line__remove" @click="emit('remove', line.key)"
        >삭제</el-button
      >
    </div>
  </div>

  <!-- PC: 기존 POS 표 -->
  <div v-else class="pos-lines" role="table" aria-label="담은 상품">
    <div class="pos-row pos-row--head" role="row">
      <span role="columnheader">구분</span>
      <span role="columnheader">품번</span>
      <span role="columnheader">상품명</span>
      <span role="columnheader">색상</span>
      <span role="columnheader">사이즈</span>
      <span role="columnheader" class="num">정가</span>
      <span role="columnheader" class="num">수량</span>
      <span role="columnheader" class="num">결제 금액</span>
      <span role="columnheader">착용자</span>
      <span role="columnheader">삭제</span>
    </div>
    <div v-if="!lines.length" class="pos-empty">{{ emptyText }}</div>
    <div v-for="line in lines" :key="line.key" class="pos-row" role="row">
      <span>{{ kindLabel }}</span>
      <span>{{ line.productCode ?? '직접' }}</span>
      <span class="pos-name">{{ line.productName }}</span>
      <span>{{ line.color ?? '-' }}</span>
      <span>{{ sizeLabel(line.size, line.itemType, line.target) }}</span>
      <span class="num">{{ line.listPrice ? formatWon(line.listPrice) : '-' }}</span>
      <span class="num">
        <el-input-number
          :model-value="line.qty"
          :min="1"
          :max="99"
          size="small"
          class="pos-qty"
          aria-label="수량"
          @update:model-value="(v) => emit('qty', line, v ?? 1)"
        />
      </span>
      <span class="num pos-amount">
        <el-input-number
          :model-value="line.lineAmount"
          :min="0"
          :step="1000"
          :controls="false"
          size="small"
          placeholder="결제 금액"
          aria-label="결제 금액"
          @update:model-value="(v) => emit('amount', line, v)"
        />
        <span v-if="line.touched" class="tag-edited">고침</span>
      </span>
      <span>
        <el-select
          v-if="hasMembers"
          :model-value="line.memberId"
          placeholder="본인"
          clearable
          size="small"
          class="pos-wearer"
          @update:model-value="(v) => emit('wearer', line, v)"
        >
          <el-option v-for="m in members" :key="m.id" :label="m.label" :value="m.id" />
        </el-select>
        <span v-else class="muted">-</span>
      </span>
      <span>
        <el-button link type="danger" aria-label="삭제" @click="emit('remove', line.key)"
          >✕</el-button
        >
      </span>
    </div>
    <div class="pos-row pos-row--total" role="row">
      <span class="pos-total-label">합계</span>
      <span class="num">{{ sum.qty }}개</span>
      <span class="num">{{ formatWon(sum.amount) }}</span>
      <span />
    </div>
  </div>
</template>

<style scoped>
.muted {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.tag-edited {
  padding: 0 6px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-warning);
  background: var(--el-color-warning-light-9);
}

/* 폰 */
.lines {
  display: flex;
  flex-direction: column;
}
.line {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}
.line:first-child {
  border-top: 0;
}
.line__product {
  grid-column: 1 / -1;
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
.line__wearer {
  width: 96px;
}

/* PC */
.pos-lines {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow-x: auto;
}
.pos-row {
  display: grid;
  grid-template-columns: 36px 56px minmax(120px, 1fr) 64px 64px 88px 96px 140px 92px 32px;
  align-items: center;
  gap: 8px;
  min-width: 860px;
  padding: 6px 12px;
  border-top: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
}
.pos-row--head {
  border-top: 0;
  background: var(--color-subtle);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
}
.pos-row--total {
  background: var(--color-subtle);
  font-weight: var(--font-weight-semibold);
}
.pos-total-label {
  grid-column: 1 / 7;
  text-align: center;
}
.pos-row .num {
  text-align: right;
  justify-self: end;
}
.pos-name {
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pos-qty {
  width: 92px;
}
.pos-amount {
  display: flex;
  align-items: center;
  gap: 4px;
}
.pos-amount :deep(.el-input-number) {
  width: 100px;
}
.pos-amount :deep(input) {
  text-align: right;
}
.pos-wearer {
  width: 88px;
}
.pos-empty {
  min-width: 860px;
  padding: 28px 12px;
  border-top: 1px solid var(--color-border);
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
</style>

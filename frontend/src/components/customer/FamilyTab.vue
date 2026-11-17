<script setup>
import { onMounted, ref } from 'vue'
import { getMembers } from '@/api/customers'
import { genderLabels, relationLabels } from '@/features/customer/customerDetail'

/**
 * 가족 구성원 (05 SCR-06, CUS-05). 이름 대신 호칭으로 받는다 (BR-13).
 * 추가·수정·삭제(매장 계정)는 다음 작업에서 만든다.
 */
const props = defineProps({ customerId: { type: Number, required: true } })
const list = ref(null)
onMounted(async () => {
  list.value = await getMembers(props.customerId)
})

const sizeKeys = [
  ['top', '상의'],
  ['outer', '아우터'],
  ['bottom', '하의'],
  ['shoes', '신발'],
]
</script>

<template>
  <div>
    <el-skeleton v-if="!list" animated :rows="2" />
    <p v-else-if="!list.length" class="fam__empty">등록한 가족 구성원이 없습니다.</p>
    <ul v-else class="fam">
      <li v-for="m in list" :key="m.id" class="member">
        <div class="member__head">
          <strong>{{ m.nickname }}</strong>
          <span class="member__meta">
            {{ relationLabels[m.relation] }}
            <template v-if="m.gender"> · {{ genderLabels[m.gender] }}</template>
            <template v-if="m.birthYear"> · {{ m.birthYear }}년생</template>
          </span>
        </div>
        <dl class="member__sizes">
          <template v-for="[k, label] in sizeKeys" :key="k">
            <template v-if="m.sizes?.[k]">
              <dt>{{ label }}</dt>
              <dd>{{ m.sizes[k] }}</dd>
            </template>
          </template>
        </dl>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.fam__empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-muted);
}
.fam {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.member {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.member__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.member__meta {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.member__sizes {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 12px;
  margin: 0;
  font-size: var(--font-size-sm);
}
.member__sizes dt {
  color: var(--color-text-muted);
}
.member__sizes dd {
  margin: 0;
}
</style>

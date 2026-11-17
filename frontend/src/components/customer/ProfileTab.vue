<script setup>
import { computed, ref } from 'vue'
import { getCustomer } from '@/api/customers'
import { channelLabels, genderLabels } from '@/features/customer/customerDetail'
import { itemTypeLabels, sizeLabel } from '@/utils/format'

/**
 * 프로필 (05 SCR-06, CUS-04). 상세주소는 가려 두고 [상세주소 보기]를 누를 때만 불러온다 (BR-08).
 * [수정](매장·본사)은 다음 작업에서 등록 폼을 다시 써서 만든다.
 */
const props = defineProps({ customer: { type: Object, required: true } })
const p = computed(() => props.customer.profile)

const address = ref(null)
const loadingAddress = ref(false)
async function showAddress() {
  loadingAddress.value = true
  address.value = (await getCustomer(props.customer.id, { includeAddress: true })).addressDetail
  loadingAddress.value = false
}

const birthday = computed(() => {
  const md = p.value.birthMd
  const md2 = md ? `${Number(md.slice(0, 2))}월 ${Number(md.slice(2))}일` : null
  return [p.value.birthYear ? `${p.value.birthYear}년` : null, md2].filter(Boolean).join(' ') || '-'
})
const region = computed(
  () => [p.value.regionSido, p.value.regionGu].filter(Boolean).join(' ') || '-',
)
const target = computed(() => ({ F: 'WOMEN', M: 'MEN' })[p.value.gender])
const sizes = computed(
  () =>
    [
      ['top', '상의', 'TOP'],
      ['outer', '아우터', 'OUTER'],
      ['bottom', '하의', 'BOTTOM'],
      ['shoes', '신발', 'SHOES'],
    ]
      .filter(([k]) => p.value.sizes?.[k])
      .map(
        ([k, label, itemType]) => `${label} ${sizeLabel(p.value.sizes[k], itemType, target.value)}`,
      )
      .join(' · ') || '-',
)
const marketing = computed(() => {
  const m = props.customer.marketing
  const on = [m.sms && '문자', m.kakao && '카톡'].filter(Boolean)
  return on.length ? `${on.join(' · ')} 동의` : '동의 안 함'
})
</script>

<template>
  <dl class="prof">
    <dt>내/외국인</dt>
    <dd>
      {{ customer.nationality === 'FOREIGN' ? '외국인' : '내국인' }}
      <template v-if="customer.nameEn"> · {{ customer.nameEn }}</template>
    </dd>
    <dt>생년·생일</dt>
    <dd>{{ birthday }}</dd>
    <dt>주소</dt>
    <dd>
      {{ region }}
      <template v-if="address !== null">
        <br />
        <span>{{ address || '상세주소 없음' }}</span>
      </template>
      <el-button
        v-else
        link
        type="primary"
        size="small"
        class="prof__addr"
        :loading="loadingAddress"
        @click="showAddress"
      >
        상세주소 보기
      </el-button>
    </dd>
    <dt>성별</dt>
    <dd>{{ genderLabels[p.gender] ?? '-' }}</dd>
    <dt>선호 품목</dt>
    <dd>{{ p.preferredItemTypes.map((t) => itemTypeLabels[t]).join(' · ') || '-' }}</dd>
    <dt>사이즈</dt>
    <dd>{{ sizes }}</dd>
    <dt>연락 채널</dt>
    <dd>{{ channelLabels[p.contactChannel] ?? '-' }}</dd>
    <dt>마케팅 수신</dt>
    <dd>{{ marketing }}</dd>
    <dt>가입매장</dt>
    <dd>{{ customer.homeStore.name }}</dd>
  </dl>
</template>

<style scoped>
.prof {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 10px 16px;
  margin: 0;
}
.prof dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.prof dd {
  margin: 0;
}
.prof__addr {
  margin-left: 8px;
}
</style>

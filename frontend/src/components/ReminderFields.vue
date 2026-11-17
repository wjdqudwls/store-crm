<script setup>
import { computed } from 'vue'
import StaffSelect from '@/components/StaffSelect.vue'

/**
 * 방문 예약·재연락 날짜와 알림 받을 직원 (01 CAL-10·11).
 * 예약은 날짜가 필수라 바로 보이고, 재연락은 토글을 켜야 날짜 칸이 나온다 (기본: 설정 안 함).
 * 알림 받을 직원 기본값은 메모를 쓰는 직원이다.
 */
const enabled = defineModel('enabled', { type: Boolean, default: false })
const date = defineModel('date', { type: String, default: null })
const staffId = defineModel('staffId', { type: Number, default: null })
const props = defineProps({
  /** true면 토글 없이 날짜 칸이 늘 보인다 (예약) */
  required: { type: Boolean, default: false },
  toggleLabel: { type: String, default: '날짜 정하기' },
  datePlaceholder: { type: String, default: '날짜' },
  /** 기본으로 알림 받을 사람 이름 (쓰는 직원) */
  writerName: { type: String, default: '' },
  error: { type: String, default: '' },
})

const open = computed(() => props.required || enabled.value)
const pastDays = (d) => {
  const t = new Date()
  t.setHours(0, 0, 0, 0)
  return d < t
}
</script>

<template>
  <div class="rmd">
    <div class="ft-inline">
      <el-switch v-if="!required" v-model="enabled" :active-text="toggleLabel" />
      <template v-if="open">
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          format="M월 D일 (dd)"
          :placeholder="datePlaceholder"
          :disabled-date="pastDays"
          class="rmd__date"
        />
        <span class="muted">알림 받을 직원</span>
        <StaffSelect
          v-model="staffId"
          :placeholder="writerName ? `${writerName} (쓰는 직원)` : '쓰는 직원'"
          class="rmd__staff"
        />
      </template>
    </div>
    <p v-if="error" class="field-error">{{ error }}</p>
  </div>
</template>

<style scoped>
.rmd {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rmd__date {
  width: 150px !important;
}
.rmd__staff {
  width: 170px;
}
</style>

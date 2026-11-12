<script setup>
import { computed } from 'vue'
import { gradeLabel } from '@/utils/format'

/** 매장 단골 등급 배지 (05 1.6 K2). 6월 임시면 점선 테두리 + "임시" */
const props = defineProps({
  grade: { type: String, required: true },
  provisional: { type: Boolean, default: false },
})
const key = computed(() => props.grade.toLowerCase())
</script>

<template>
  <span
    class="grade"
    :class="{ 'grade--provisional': provisional }"
    :style="{ background: `var(--grade-${key}-bg)`, color: `var(--grade-${key}-text)` }"
    :aria-label="`매장 단골 등급 ${gradeLabel(grade)}`"
  >
    {{ gradeLabel(grade) }}<template v-if="provisional"> 임시</template>
  </span>
</template>

<style scoped>
.grade {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 4px;
  border: 1px solid transparent;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  line-height: 1;
}
.grade--provisional {
  border: 1px dashed currentColor;
}
</style>

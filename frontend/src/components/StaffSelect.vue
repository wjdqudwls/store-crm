<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStaff } from '@/api/sales'
import { useSaleRecentStore } from '@/stores/saleRecent'

/**
 * 직원 고르기 (05 1.3). 칸은 비어 있고, 열면 직전에 고른 직원이 맨 위에 있다 (S2·S9).
 */
const model = defineModel({ type: Number, default: null })
defineProps({ placeholder: { type: String, default: '직원을 고르세요' } })

const recent = useSaleRecentStore()
const staff = ref([])

onMounted(async () => {
  staff.value = await getStaff()
})

const options = computed(() => {
  const last = staff.value.find((s) => s.id === recent.lastStaffId)
  return last ? [last, ...staff.value.filter((s) => s.id !== last.id)] : staff.value
})

const roleNames = { MANAGER: '매니저', SENIOR: '시니어', JUNIOR: '주니어', STAFF: '스태프' }

function onChange(id) {
  if (id != null) recent.pickStaff(id)
}

const selectRef = ref()
defineExpose({ focus: () => selectRef.value?.focus() })
</script>

<template>
  <el-select
    ref="selectRef"
    v-model="model"
    :placeholder="placeholder"
    filterable
    clearable
    class="staff-select"
    @change="onChange"
  >
    <el-option v-for="s in options" :key="s.id" :label="s.name" :value="s.id">
      <span>{{ s.name }}</span>
      <span class="staff-select__role">{{ roleNames[s.role] }}</span>
      <span v-if="s.id === recent.lastStaffId" class="staff-select__last">직전</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.staff-select {
  width: 100%;
}
.staff-select__role {
  margin-left: 8px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.staff-select__last {
  float: right;
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}
</style>

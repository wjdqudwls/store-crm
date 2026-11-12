<script setup>
/**
 * 허용값만 버튼으로 고르기 (05 1.3 사이즈 선택). 고른 버튼을 다시 누르면 선택을 뺀다.
 * multiple이면 배열 값으로 여러 개 고른다 (선호 품목).
 */
const model = defineModel({ type: [String, Array, null], default: null })
const props = defineProps({
  /** [{ value, label }] */
  options: { type: Array, required: true },
  multiple: { type: Boolean, default: false },
  size: { type: String, default: 'default' },
})

function selected(v) {
  return props.multiple ? (model.value ?? []).includes(v) : model.value === v
}

function toggle(v) {
  if (props.multiple) {
    const cur = model.value ?? []
    model.value = cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v]
  } else {
    model.value = model.value === v ? null : v
  }
}
</script>

<template>
  <div class="choices" role="group">
    <el-button
      v-for="o in options"
      :key="o.value"
      :type="selected(o.value) ? 'primary' : 'default'"
      :plain="!selected(o.value)"
      :size="size"
      :aria-pressed="selected(o.value)"
      class="choices__btn"
      @click="toggle(o.value)"
    >
      {{ o.label }}
    </el-button>
  </div>
</template>

<style scoped>
.choices {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.choices__btn {
  margin: 0;
  min-width: 52px;
}
</style>

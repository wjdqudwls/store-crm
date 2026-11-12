<script setup>
import { nextTick, ref } from 'vue'
import { searchCustomers } from '@/api/sales'
import GradeBadge from './GradeBadge.vue'

/**
 * 고객 찾기 (05 1.3): 이름 입력 → Enter → 목록 → 클릭 또는 ↑↓ Enter.
 * 결과가 없으면 "새 고객 등록"과 "비회원으로 기록".
 */
const emit = defineEmits(['select', 'register', 'non-member'])

const name = ref('')
const results = ref(null)
const active = ref(0)
const loading = ref(false)
const inputRef = ref()

async function search() {
  if (!name.value.trim()) return
  loading.value = true
  const res = await searchCustomers(name.value)
  results.value = res.content
  active.value = 0
  loading.value = false
}

function move(step) {
  if (!results.value?.length) return
  active.value = (active.value + step + results.value.length) % results.value.length
}

function onEnter() {
  if (results.value?.length && results.value[active.value]) {
    choose(results.value[active.value])
  } else {
    search()
  }
}

function choose(c) {
  emit('select', c)
  reset()
}

function reset() {
  name.value = ''
  results.value = null
}

function daysAgo(iso) {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000)
  return days <= 0 ? '오늘' : `${days}일 전`
}

defineExpose({
  focus: () => nextTick(() => inputRef.value?.focus()),
})
</script>

<template>
  <div class="customer-search">
    <el-input
      ref="inputRef"
      v-model="name"
      placeholder="고객 이름 입력 후 Enter"
      clearable
      @keydown.enter.prevent="onEnter"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @input="results = null"
    >
      <template #append>
        <el-button :loading="loading" @click="search">찾기</el-button>
      </template>
    </el-input>

    <ul
      v-if="results?.length"
      class="customer-search__list"
      role="listbox"
      aria-label="고객 검색 결과"
    >
      <li
        v-for="(c, i) in results"
        :key="c.id"
        role="option"
        :aria-selected="i === active"
        class="customer-search__item"
        :class="{ 'is-active': i === active }"
        @click="choose(c)"
        @mouseenter="active = i"
      >
        <span class="customer-search__name">{{ c.name }}</span>
        <span class="customer-search__phone">{{ c.phoneMasked }}</span>
        <GradeBadge :grade="c.grade" />
        <span class="customer-search__meta"
          >마지막 방문 {{ daysAgo(c.lastVisitAt) }} · 담당 {{ c.staffName }}</span
        >
      </li>
    </ul>

    <div v-else-if="results" class="customer-search__empty">
      <span>'{{ name }}' 고객이 없습니다</span>
      <el-button size="small" type="primary" plain @click="emit('register', name)"
        >새 고객 등록</el-button
      >
      <el-button size="small" @click="(emit('non-member'), reset())">비회원으로 기록</el-button>
    </div>
  </div>
</template>

<style scoped>
.customer-search__list {
  list-style: none;
  margin: 6px 0 0;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
}
.customer-search__item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: var(--touch-min);
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
}
.customer-search__item.is-active {
  background: var(--el-color-primary-light-9);
}
.customer-search__name {
  font-weight: var(--font-weight-semibold);
}
.customer-search__phone,
.customer-search__meta {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.customer-search__meta {
  margin-left: auto;
}
.customer-search__empty {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
</style>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchCustomers } from '@/api/customers'
import GradeBadge from '@/components/GradeBadge.vue'
import { useSessionStore } from '@/stores/session'
import { daysAgoLabel } from '@/features/customer/customerDetail'

/**
 * 고객 검색 `/customers` (05 SCR-06). 이름 + Enter → 줄을 누르면 상세 (터치 2번, NFR-08).
 * 검색어는 주소(`?name=`)에 두어 상세에서 돌아오면 목록이 그대로다.
 */
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const name = ref(String(route.query.name ?? ''))
const results = ref(null)
const searched = ref('')
const loading = ref(false)
const inputRef = ref()

async function search() {
  const q = name.value.trim()
  if (!q) return
  loading.value = true
  router.replace({ query: { name: q } })
  results.value = (await searchCustomers(q)).content
  searched.value = q
  loading.value = false
}

onMounted(() => {
  inputRef.value?.focus()
  if (name.value) search()
})

const sameNames = computed(() => {
  const list = results.value ?? []
  return list.length > 1 && new Set(list.map((c) => c.name)).size < list.length
})

function open(c) {
  router.push({ path: `/customers/${c.id}`, query: { from: 'search' } })
}
</script>

<template>
  <div class="clist">
    <header class="clist__head">
      <h1 class="clist__title">고객</h1>
      <el-button v-if="session.isStore" type="primary" @click="router.push('/customers/new')">
        고객 등록
      </el-button>
    </header>

    <form class="clist__search" @submit.prevent="search">
      <el-input
        ref="inputRef"
        v-model="name"
        size="large"
        placeholder="고객 이름 입력 후 Enter"
        clearable
        aria-label="고객 이름"
      />
      <el-button size="large" native-type="submit" :loading="loading">찾기</el-button>
    </form>

    <!-- 불러오는 중: 회색 줄 (05 1.5) -->
    <div v-if="loading && !results" class="clist__list">
      <el-skeleton v-for="i in 3" :key="i" animated :rows="0" class="clist__skeleton" />
    </div>

    <template v-else-if="results">
      <p v-if="sameNames" class="clist__hint">
        같은 이름이 여럿입니다. 전화번호 뒷자리로 구별하세요.
      </p>

      <ul v-if="results.length" class="clist__list" aria-label="검색 결과">
        <li v-for="c in results" :key="c.id">
          <button type="button" class="crow" @click="open(c)">
            <span class="crow__name">{{ c.name }}</span>
            <span class="crow__phone">{{ c.phoneMasked }}</span>
            <GradeBadge :grade="c.grade" />
            <span class="crow__visit">
              <span class="crow__label">마지막 방문</span> {{ daysAgoLabel(c.lastVisitAt) }}
            </span>
            <span class="crow__staff">
              <span class="crow__label">담당</span> {{ c.staffName ?? '없음' }}
            </span>
            <span class="crow__go" aria-hidden="true">›</span>
          </button>
        </li>
      </ul>

      <div v-else class="clist__empty">
        <p>'{{ searched }}' 고객이 없습니다.</p>
        <el-button
          v-if="session.isStore"
          type="primary"
          plain
          @click="router.push('/customers/new')"
        >
          새 고객 등록
        </el-button>
      </div>
    </template>

    <p v-else class="clist__hint">이름 일부만 입력해도 찾습니다. 예: 김하늘, 오세진</p>
  </div>
</template>

<style scoped>
.clist {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.clist__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.clist__title {
  margin: 0;
  font-size: var(--font-size-xl);
}
.clist__search {
  display: flex;
  gap: 8px;
}
.clist__hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.clist__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}
.clist__list li + li {
  border-top: 1px solid var(--color-border);
}
.clist__skeleton {
  padding: 16px;
}
.crow {
  width: 100%;
  display: grid;
  grid-template-columns: 120px 90px 56px 1fr 140px 16px;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.crow:hover,
.crow:focus-visible {
  background: var(--color-subtle);
}
.crow__name {
  font-weight: var(--font-weight-semibold);
}
.crow__phone,
.crow__visit,
.crow__staff {
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}
.crow__label {
  color: var(--color-text-muted);
}
.crow__go {
  color: var(--color-text-muted);
}
.clist__empty {
  padding: 24px;
  text-align: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}
.clist__empty p {
  margin: 0 0 12px;
}

@media (max-width: 767.98px) {
  .clist__title {
    display: none;
  }
  .clist__head {
    justify-content: flex-end;
  }
  .crow {
    grid-template-columns: auto auto 1fr 16px;
    grid-template-areas:
      'name phone grade go'
      'visit staff staff go';
    row-gap: 4px;
  }
  .crow__name {
    grid-area: name;
  }
  .crow__phone {
    grid-area: phone;
  }
  .crow :deep(.grade) {
    grid-area: grade;
    justify-self: start;
  }
  .crow__visit {
    grid-area: visit;
  }
  .crow__staff {
    grid-area: staff;
  }
  .crow__go {
    grid-area: go;
  }
}
</style>

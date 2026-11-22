<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import { useRouter } from 'vue-router'
import GradeBadge from '@/components/GradeBadge.vue'
import StaffSelect from '@/components/StaffSelect.vue'
import ContactResultDialog from '@/components/ContactResultDialog.vue'
import { getCallList } from '@/api/customers'
import { getStores } from '@/api/stores'
import { useSessionStore } from '@/stores/session'
import { daysAgoLabel } from '@/features/customer/customerDetail'
import { gradeLabel, itemTypeLabels, targetLabels } from '@/utils/format'
import { relationLabels } from '@/features/customer/customerDetail'
import { useIsPhone } from '@/composables/useIsPhone'

/**
 * 콜리스트 `/call-list` (05 SCR-07, CAL-01, BR-09). 마지막 방문이 오래된 순.
 * 기본 조건: 직원 = 내 담당 · D 이상 · 90일 이상 미방문, 매장 계정 = 이 매장 전체 · 홍보 연락 가능만.
 */
const router = useRouter()
const session = useSessionStore()
const isPhone = useIsPhone()

const defaults = () =>
  session.isStore || session.isHq
    ? { scope: 'STORE', staffId: null, minGrade: null, inactiveDays: null, promoOnly: true }
    : { scope: 'MINE', staffId: null, minGrade: 'D', inactiveDays: 90, promoOnly: false }

const filters = reactive({
  ...defaults(),
  storeId: null,
  target: null,
  itemType: null,
  memberRelation: null,
})
const draft = reactive({ ...filters })
const list = ref(null)

const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    list.value = null
    list.value = (await getCallList({ ...filters })).content
  })
// 본사는 매장을 골라야 콜리스트를 볼 수 있다 (03: storeId 필수). 처음에는 첫 매장
const stores = ref([])
onMounted(async () => {
  if (session.isHq) {
    try {
      stores.value = await getStores()
      draft.storeId = filters.storeId = stores.value[0]?.id ?? null
    } catch {
      // 매장 목록이 없으면 서버가 매장을 고르라는 오류를 돌려주고 아래 띠로 보인다
    }
  }
  load()
})

const summary = computed(() => {
  const parts = [filters.scope === 'MINE' ? '내 담당' : '이 매장 고객 전체']
  if (filters.minGrade) parts.push(`${gradeLabel(filters.minGrade)} 이상`)
  if (filters.inactiveDays) parts.push(`${filters.inactiveDays}일 이상 미방문`)
  if (filters.target) parts.push(targetLabels[filters.target])
  if (filters.itemType) parts.push(`선호 ${itemTypeLabels[filters.itemType]}`)
  if (filters.memberRelation) parts.push(`${relationLabels[filters.memberRelation]} 있음`)
  if (filters.promoOnly) parts.push('홍보 연락 가능만')
  return parts.join(' · ')
})

const dialogOpen = ref(false)
function openFilters() {
  Object.assign(draft, filters)
  dialogOpen.value = true
}
function applyFilters() {
  Object.assign(filters, draft)
  dialogOpen.value = false
  load()
}
function resetDraft() {
  Object.assign(draft, defaults(), {
    storeId: filters.storeId,
    target: null,
    itemType: null,
    memberRelation: null,
  })
}

function visitLabel(c) {
  if (c.lastVisitAt) return `마지막 방문 ${daysAgoLabel(c.lastVisitAt)}`
  const days = daysAgoLabel(c.createdAt).replace(' 전', '')
  return `가입 후 ${days}`
}

const resultOpen = ref(false)
const resultCustomer = ref(null)
function openResult(c) {
  resultCustomer.value = c
  resultOpen.value = true
}

const grades = ['A', 'B', 'C', 'D']
</script>

<template>
  <div class="page" :class="{ 'page--narrow': isPhone }">
    <header class="page__head">
      <h1 class="page__title">콜리스트</h1>
      <div v-if="!isPhone" class="page__actions">
        <el-button @click="openFilters">조건 바꾸기</el-button>
        <el-button type="primary" @click="load">조회</el-button>
      </div>
    </header>

    <div v-if="!isPhone" class="ftable">
      <div class="ft-label">조회 조건</div>
      <div class="ft-value ft-wide">{{ summary }}</div>
    </div>
    <div v-else class="card cl__cond">
      <span>{{ summary }}</span>
      <el-button size="small" @click="openFilters">조건 바꾸기</el-button>
    </div>

    <LoadError :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-if="!list && !error" animated :rows="4" class="card" />
    <div v-else-if="list && !list.length" class="card empty">
      조건에 맞는 고객이 없습니다.
      <el-button link type="primary" @click="openFilters">조건 바꾸기</el-button>
    </div>
    <template v-else-if="list">
      <p class="muted">{{ list.length }}명 · 마지막 방문이 오래된 순</p>
      <section v-if="!isPhone" class="card">
        <el-table
          :data="list"
          row-class-name="clickable-row"
          @row-click="
            (c) => router.push({ path: `/customers/${c.id}`, query: { from: 'call-list' } })
          "
        >
          <el-table-column prop="name" label="고객명" min-width="110" />
          <el-table-column prop="phoneMasked" label="연락처" width="110" />
          <el-table-column label="등급" width="80">
            <template #default="{ row }"><GradeBadge :grade="row.grade" /></template>
          </el-table-column>
          <el-table-column label="마지막 방문" width="150">
            <template #default="{ row }">{{
              visitLabel(row).replace('마지막 방문 ', '')
            }}</template>
          </el-table-column>
          <el-table-column label="담당 직원" width="110">
            <template #default="{ row }">{{ row.staffName ?? '없음' }}</template>
          </el-table-column>
          <el-table-column label="홍보 연락" width="100">
            <template #default="{ row }">
              <span :class="{ cl__nopromo: !row.marketingAllowed }">
                {{ row.marketingAllowed ? '가능' : '불가' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="!session.isHq" width="110">
            <template #default="{ row }">
              <el-button size="small" @click.stop="openResult(row)">연락 결과</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
      <ul v-else class="cl__list card">
        <li v-for="c in list" :key="c.id" class="cl__row">
          <button
            type="button"
            class="cl__body"
            @click="router.push({ path: `/customers/${c.id}`, query: { from: 'call-list' } })"
          >
            <span class="cl__who">
              <strong>{{ c.name }}</strong>
              <GradeBadge :grade="c.grade" />
              <span class="muted">{{ c.phoneMasked }}</span>
            </span>
            <span class="muted">
              {{ visitLabel(c) }} · 담당 {{ c.staffName ?? '없음' }}
              <span v-if="!c.marketingAllowed" class="cl__nopromo">· 홍보 연락 불가</span>
            </span>
          </button>
          <el-button v-if="!session.isHq" size="small" @click="openResult(c)">연락 결과</el-button>
        </li>
      </ul>
    </template>

    <el-dialog v-model="dialogOpen" title="콜리스트 조건" width="min(520px, 94vw)" align-center>
      <el-form label-position="top" class="cl__form">
        <el-form-item v-if="session.isHq" label="매장">
          <el-select v-model="draft.storeId">
            <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="범위">
          <el-radio-group v-model="draft.scope">
            <el-radio-button value="MINE" :disabled="session.isStore">내 담당</el-radio-button>
            <el-radio-button value="STORE">이 매장 전체</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="session.isStore" label="담당 직원">
          <StaffSelect v-model="draft.staffId" placeholder="전체" />
        </el-form-item>
        <el-form-item label="최소 등급">
          <el-select v-model="draft.minGrade" placeholder="전체" clearable>
            <el-option v-for="g in grades" :key="g" :label="`${g} 이상`" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="마지막 방문 후">
          <el-input-number v-model="draft.inactiveDays" :min="0" :step="30" placeholder="일" />
          <span class="muted cl__unit">일 이상</span>
        </el-form-item>
        <el-form-item label="대상">
          <el-select v-model="draft.target" placeholder="전체" clearable>
            <el-option v-for="(l, v) in targetLabels" :key="v" :label="l" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="선호 품목">
          <el-select v-model="draft.itemType" placeholder="전체" clearable>
            <el-option v-for="(l, v) in itemTypeLabels" :key="v" :label="l" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="가족 구성원">
          <el-select v-model="draft.memberRelation" placeholder="상관없음" clearable>
            <el-option v-for="(l, v) in relationLabels" :key="v" :label="`${l} 있음`" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="draft.promoOnly">홍보 연락 가능한 고객만</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDraft">기본 조건으로</el-button>
        <el-button type="primary" @click="applyFilters">적용</el-button>
      </template>
    </el-dialog>

    <ContactResultDialog v-model="resultOpen" :customer="resultCustomer" />
  </div>
</template>

<style scoped>
.cl__cond {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: var(--font-size-sm);
}
.cl__list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.cl__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
}
.cl__row + .cl__row {
  border-top: 1px solid var(--color-border);
}
.cl__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.cl__who {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cl__nopromo {
  color: var(--color-warning);
}
.cl__form :deep(.el-select) {
  width: 100%;
}
.cl__unit {
  margin-left: 8px;
}
</style>

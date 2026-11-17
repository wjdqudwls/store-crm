<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GradeBadge from '@/components/GradeBadge.vue'
import GradeSummary from '@/components/customer/GradeSummary.vue'
import PurchaseTab from '@/components/customer/PurchaseTab.vue'
import NoteTab from '@/components/customer/NoteTab.vue'
import FollowUpTab from '@/components/customer/FollowUpTab.vue'
import FamilyTab from '@/components/customer/FamilyTab.vue'
import ProfileTab from '@/components/customer/ProfileTab.vue'
import { getCustomer } from '@/api/customers'
import { useIsPhone } from '@/composables/useIsPhone'
import { firstTab, marketingAllowed, pcTabs, phoneTabs } from '@/features/customer/customerDetail'

/**
 * 고객 상세 `/customers/{id}?tab=` (05 SCR-06, D1~D4).
 * PC: 왼쪽 요약을 늘 보이고 오른쪽 탭 5개. 폰: 머리를 줄이고 탭 6개(요약 포함)를 가로로 넘긴다.
 */
const route = useRoute()
const router = useRouter()
const isPhone = useIsPhone()

const customer = ref(null)
const notFound = ref(false)

async function load(id) {
  customer.value = null
  notFound.value = false
  try {
    customer.value = await getCustomer(id)
  } catch (e) {
    if (e?.status === 404) notFound.value = true
    else throw e
  }
}
watch(
  () => route.params.id,
  (id) => id && load(Number(id)),
  { immediate: true },
)

const tabLabels = {
  summary: '요약',
  purchases: '구매이력',
  notes: '메모',
  followUps: '후속 연락',
  family: '가족',
  profile: '프로필',
}
const phoneLabels = { ...tabLabels, purchases: '구매', followUps: '할 일' }

const tabs = computed(() => (isPhone.value ? phoneTabs : pcTabs))
const tab = computed(() => firstTab(route.query.from, isPhone.value, route.query.tab))
function selectTab(t) {
  router.replace({ query: { ...route.query, tab: t } })
}

const promo = computed(() => customer.value && marketingAllowed(customer.value.marketing))
const provisional = computed(() => customer.value?.grade.confirmedStatus === 'PROVISIONAL')

function back() {
  if (route.query.from === 'search' && window.history.state?.back) router.back()
  else router.push('/customers')
}
</script>

<template>
  <div class="cdetail">
    <el-button link class="cdetail__back" @click="back">‹ 고객 목록</el-button>

    <div v-if="notFound" class="cdetail__notfound">
      <p>찾을 수 없습니다.</p>
      <el-button @click="router.push('/customers')">목록으로 가기</el-button>
    </div>

    <el-skeleton v-else-if="!customer" animated :rows="6" class="cdetail__card" />

    <template v-else>
      <!-- 상세 머리: 모든 탭 위에 고정 -->
      <header class="chead cdetail__card">
        <div class="chead__line">
          <h1 class="chead__name">{{ customer.name }}</h1>
          <span class="chead__phone">{{ customer.phone }}</span>
        </div>
        <div class="chead__line">
          <span class="chead__muted">매장 단골 등급</span>
          <GradeBadge :grade="customer.grade.current" :provisional="provisional" />
          <span v-if="provisional" class="chead__muted">7/1 확정</span>
          <span v-if="customer.grade.discountRate" class="chead__discount">
            할인 {{ customer.grade.discountRate }}% 대상
          </span>
          <span class="chead__muted">담당 {{ customer.staff?.name ?? '없음' }}</span>
          <el-tag :type="promo ? 'success' : 'info'" size="small" effect="light">
            {{ promo ? '홍보 연락 가능' : '홍보 연락 불가' }}
          </el-tag>
        </div>
      </header>

      <div class="cdetail__body">
        <aside v-if="!isPhone" class="cdetail__side cdetail__card">
          <GradeSummary :customer="customer" />
        </aside>

        <section class="cdetail__main cdetail__card">
          <nav class="ctabs" role="tablist" aria-label="고객 정보">
            <button
              v-for="t in tabs"
              :key="t"
              type="button"
              role="tab"
              class="ctabs__tab"
              :class="{ 'ctabs__tab--on': tab === t }"
              :aria-selected="tab === t"
              @click="selectTab(t)"
            >
              {{ (isPhone ? phoneLabels : tabLabels)[t] }}
            </button>
          </nav>

          <div class="ctabs__panel" role="tabpanel">
            <GradeSummary v-if="tab === 'summary'" :customer="customer" />
            <PurchaseTab v-else-if="tab === 'purchases'" :customer-id="customer.id" />
            <NoteTab v-else-if="tab === 'notes'" :customer-id="customer.id" />
            <FollowUpTab
              v-else-if="tab === 'followUps'"
              :customer-id="customer.id"
              :marketing-allowed="promo"
            />
            <FamilyTab v-else-if="tab === 'family'" :customer-id="customer.id" />
            <ProfileTab v-else-if="tab === 'profile'" :customer="customer" />
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cdetail {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cdetail__back {
  align-self: flex-start;
  padding-left: 0;
}
.cdetail__card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
}
.cdetail__notfound {
  padding: 32px;
  text-align: center;
}
.chead {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chead__line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 12px;
}
.chead__name {
  margin: 0;
  font-size: var(--font-size-2xl);
}
.chead__phone {
  font-size: var(--font-size-lg);
  font-variant-numeric: tabular-nums;
}
.chead__muted {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.chead__discount {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
}
.cdetail__body {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 12px;
  align-items: start;
}
.cdetail__side {
  position: sticky;
  top: 72px;
}
.ctabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-border);
  margin: -4px 0 16px;
  overflow-x: auto;
  scrollbar-width: none;
}
.ctabs__tab {
  flex: 0 0 auto;
  padding: 10px 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  font: inherit;
  color: var(--color-text-muted);
  cursor: pointer;
}
.ctabs__tab--on {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

@media (max-width: 767.98px) {
  .cdetail__body {
    grid-template-columns: 1fr;
  }
  .cdetail__card {
    padding: 12px;
  }
  .chead__name {
    font-size: var(--font-size-xl);
  }
  .ctabs__tab {
    padding: 10px 12px;
  }
}
</style>

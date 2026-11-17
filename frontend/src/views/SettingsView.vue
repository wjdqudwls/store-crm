<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import GradeBadge from '@/components/GradeBadge.vue'
import {
  createGradePolicy,
  getFollowUpRules,
  getGradePolicy,
  nextJuneFirst,
  runPeriodic,
  saveDiscounts,
  saveFollowUpRules,
} from '@/api/settings'
import { useSessionStore } from '@/stores/session'
import { clone } from '@/api/fake'
import { formatWon } from '@/utils/format'

/**
 * 등급 기준 · 할인율 · 후속 연락 설정 `/settings/grade` (05 SCR-09, GRD-04·05, CAL-07).
 * 본사는 고치고, 매장 계정은 보기만 ("기준 보기"). 등급 코드 5개와 시즌은 바꾸는 칸이 없다 (W11).
 */
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const canEdit = computed(() => session.isHq)

const tabs = computed(() => ({
  grade: '등급 기준',
  discount: '할인율',
  'follow-up': '후속 연락',
  ...(canEdit.value ? { periodic: '정기 산정' } : {}),
}))
const tab = computed(() => (tabs.value[route.query.tab] ? route.query.tab : 'grade'))
const selectTab = (t) => router.replace({ query: { tab: t } })

const policy = ref(null)
const rules = ref(null)
async function load() {
  ;[policy.value, rules.value] = await Promise.all([getGradePolicy(), getFollowUpRules()])
}
onMounted(load)

const nextDate = nextJuneFirst()
const inJune = new Date().getMonth() === 5

// ---- 새 기준 만들기 (HQ) ----
const editing = ref(null)
const editError = ref('')
function startEdit() {
  const { amountRules, ticketRules, gradeCuts } = clone(policy.value)
  editing.value = { amountRules, ticketRules, gradeCuts }
  editError.value = ''
}
async function saveEdit() {
  try {
    await ElMessageBox.confirm(
      inJune
        ? '지금 바로 적용하고 정기 산정을 다시 실행합니다.'
        : `다음 6월 1일(${nextDate})부터 적용됩니다. 시즌 중에는 지금 기준을 그대로 씁니다.`,
      '새 기준 저장',
      { confirmButtonText: '저장', cancelButtonText: '돌아가기' },
    )
  } catch {
    return
  }
  try {
    await createGradePolicy(editing.value)
    editing.value = null
    ElMessage.success('새 기준을 저장했습니다')
    load()
  } catch (e) {
    editError.value = e?.body?.message ?? '저장됐는지 확인하지 못했습니다'
  }
}

// ---- 할인율 ----
const discountDraft = ref(null)
function startDiscount() {
  discountDraft.value = clone(policy.value.discounts)
}
async function saveDiscount() {
  try {
    await saveDiscounts(discountDraft.value)
    discountDraft.value = null
    ElMessage.success('할인율을 저장했습니다. 바로 적용됩니다')
    load()
  } catch (e) {
    ElMessage.error(e?.body?.message ?? '저장됐는지 확인하지 못했습니다')
  }
}

// ---- 후속 연락 단계 ----
const rulesDraft = ref(null)
const unitLabels = { DAY: '일', WEEK: '주', MONTH: '달' }
function startRules() {
  rulesDraft.value = clone(rules.value)
}
function addRule() {
  rulesDraft.value.push({
    id: Date.now(),
    name: '',
    amount: 1,
    unit: 'MONTH',
    promotional: false,
    enabled: true,
  })
}
function moveRule(i, step) {
  const list = rulesDraft.value
  const j = i + step
  if (j < 0 || j >= list.length) return
  ;[list[i], list[j]] = [list[j], list[i]]
}
async function saveRules() {
  try {
    await saveFollowUpRules(rulesDraft.value)
    rulesDraft.value = null
    ElMessage.success('저장했습니다. 이후 구매부터 적용됩니다. 이미 만든 할 일은 그대로입니다')
    load()
  } catch (e) {
    ElMessage.error(e?.body?.message ?? '저장됐는지 확인하지 못했습니다')
  }
}

// ---- 정기 산정 수동 실행 ----
const running = ref(false)
const runResult = ref(null)
async function run() {
  try {
    await ElMessageBox.confirm(
      '전체 고객 등급을 막 끝난 시즌 실적으로 다시 정합니다. 몇 번 실행해도 결과가 같습니다.',
      '정기 산정 실행',
      { confirmButtonText: '실행', cancelButtonText: '돌아가기' },
    )
  } catch {
    return
  }
  running.value = true
  runResult.value = await runPeriodic()
  running.value = false
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">{{ canEdit ? '설정' : '기준 보기' }}</h1>
    </header>

    <el-skeleton v-if="!policy" animated :rows="5" class="card" />
    <section v-else class="card">
      <nav class="set__tabs" role="tablist">
        <button
          v-for="(label, t) in tabs"
          :key="t"
          type="button"
          role="tab"
          class="set__tab"
          :class="{ 'set__tab--on': tab === t }"
          :aria-selected="tab === t"
          @click="selectTab(t)"
        >
          {{ label }}
        </button>
      </nav>

      <!-- 등급 기준 -->
      <template v-if="tab === 'grade'">
        <p v-if="policy.next" class="draft-note">
          {{ policy.next.appliesFrom }}부터 새 기준 (버전 {{ policy.next.version }})
        </p>
        <p class="muted">
          버전 {{ policy.version }} · {{ policy.appliesFrom }}부터 · 시즌은 6월 1일부터 12개월. 이번
          시즌 금액 점수 + 티켓 점수 = 합계 점수로 등급을 정합니다.
        </p>

        <div v-if="!editing" class="set__grid">
          <el-table :data="policy.amountRules" size="small">
            <el-table-column label="시즌 순구매금액">
              <template #default="{ row }">{{ formatWon(row.minValue) }} 이상</template>
            </el-table-column>
            <el-table-column prop="score" label="금액 점수" width="100" align="right" />
          </el-table>
          <el-table :data="policy.ticketRules" size="small">
            <el-table-column label="시즌 티켓 수">
              <template #default="{ row }">{{ row.minValue }}건 이상</template>
            </el-table-column>
            <el-table-column prop="score" label="티켓 점수" width="100" align="right" />
          </el-table>
          <el-table :data="policy.gradeCuts" size="small">
            <el-table-column label="등급">
              <template #default="{ row }"><GradeBadge :grade="row.grade" /></template>
            </el-table-column>
            <el-table-column label="합계 점수" width="120" align="right">
              <template #default="{ row }">{{ row.minScore }}점 이상</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else class="set__grid">
          <div
            v-for="(label, key) in { amountRules: '금액 구간(원)', ticketRules: '티켓 구간(건)' }"
            :key="key"
          >
            <h3 class="set__h">{{ label }}</h3>
            <div v-for="(r, i) in editing[key]" :key="i" class="set__row">
              <el-input-number v-model="r.minValue" :min="0" :controls="false" size="small" />
              <span class="muted">이상 →</span>
              <el-input-number
                v-model="r.score"
                :min="0"
                :controls="false"
                size="small"
                class="set__score"
              />
              <span class="muted">점</span>
            </div>
          </div>
          <div>
            <h3 class="set__h">등급 컷 (점)</h3>
            <div v-for="c in editing.gradeCuts" :key="c.grade" class="set__row">
              <GradeBadge :grade="c.grade" />
              <el-input-number
                v-model="c.minScore"
                :min="0"
                :controls="false"
                size="small"
                class="set__score"
              />
              <span class="muted">점 이상</span>
            </div>
          </div>
        </div>
        <p v-if="editError" class="field-error">{{ editError }}</p>

        <div v-if="canEdit" class="set__actions">
          <template v-if="editing">
            <el-button @click="editing = null">돌아가기</el-button>
            <el-button type="primary" @click="saveEdit">저장</el-button>
          </template>
          <el-button v-else type="primary" @click="startEdit">새 기준 만들기</el-button>
        </div>
        <p class="muted">
          지난 버전:
          <span v-for="v in policy.versions" :key="v.version"
            >버전 {{ v.version }} ({{ v.appliesFrom }})
          </span>
        </p>
      </template>

      <!-- 할인율 -->
      <template v-else-if="tab === 'discount'">
        <p class="muted">
          등급별 할인율은 판매 기록의 결제 금액 기본값에 씁니다. 바꾸면 바로 적용됩니다.
        </p>
        <el-table :data="discountDraft ?? policy.discounts" size="small" class="set__narrow">
          <el-table-column label="등급">
            <template #default="{ row }"><GradeBadge :grade="row.grade" /></template>
          </el-table-column>
          <el-table-column label="할인율" width="160" align="right">
            <template #default="{ row }">
              <el-input-number
                v-if="discountDraft"
                v-model="row.rate"
                :min="0"
                :max="100"
                size="small"
              />
              <span v-else>{{ row.rate }}%</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="canEdit" class="set__actions">
          <template v-if="discountDraft">
            <el-button @click="discountDraft = null">돌아가기</el-button>
            <el-button type="primary" @click="saveDiscount">저장</el-button>
          </template>
          <el-button v-else @click="startDiscount">고치기</el-button>
        </div>
      </template>

      <!-- 후속 연락 -->
      <template v-else-if="tab === 'follow-up'">
        <p class="muted">
          회원 구매 뒤 만들 연락 할 일 단계입니다. 홍보 단계는 문자·카톡 수신 동의가 없으면 안부
          연락만 합니다.
        </p>
        <el-table v-if="!rulesDraft" :data="rules" size="small">
          <el-table-column prop="name" label="단계" min-width="160" />
          <el-table-column label="구매 뒤" width="100">
            <template #default="{ row }">{{ row.amount }}{{ unitLabels[row.unit] }}</template>
          </el-table-column>
          <el-table-column label="홍보" width="80">
            <template #default="{ row }">{{ row.promotional ? '홍보' : '-' }}</template>
          </el-table-column>
          <el-table-column label="사용" width="80">
            <template #default="{ row }">{{ row.enabled ? '켜짐' : '꺼짐' }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="set__rules">
          <div v-for="(r, i) in rulesDraft" :key="r.id" class="set__row set__row--rule">
            <el-input v-model="r.name" placeholder="단계 이름" size="small" class="set__name" />
            <el-input-number v-model="r.amount" :min="1" size="small" class="set__score" />
            <el-select v-model="r.unit" size="small" class="set__unit">
              <el-option v-for="(l, u) in unitLabels" :key="u" :label="l" :value="u" />
            </el-select>
            <el-checkbox v-model="r.promotional">홍보</el-checkbox>
            <el-switch v-model="r.enabled" />
            <el-button size="small" text @click="moveRule(i, -1)">▲</el-button>
            <el-button size="small" text @click="moveRule(i, 1)">▼</el-button>
            <el-button size="small" text type="danger" @click="rulesDraft.splice(i, 1)"
              >삭제</el-button
            >
          </div>
          <el-button size="small" @click="addRule">단계 추가</el-button>
        </div>
        <div v-if="canEdit" class="set__actions">
          <template v-if="rulesDraft">
            <el-button @click="rulesDraft = null">돌아가기</el-button>
            <el-button type="primary" @click="saveRules">저장</el-button>
          </template>
          <el-button v-else @click="startRules">고치기</el-button>
        </div>
      </template>

      <!-- 정기 산정 -->
      <template v-else-if="tab === 'periodic'">
        <p class="muted">
          매년 6월 1일에 자동으로 돕니다. 데모용으로 지금 실행할 수 있고, 몇 번 실행해도 결과가
          같습니다 (GRD-05).
        </p>
        <el-button type="primary" :loading="running" @click="run">지금 실행</el-button>
        <dl v-if="runResult" class="set__result">
          <dt>시즌</dt>
          <dd>
            {{ runResult.season }} → {{ runResult.nextSeason }} (기준 버전
            {{ runResult.policyVersion }})
          </dd>
          <dt>고객</dt>
          <dd>{{ runResult.customerCount.toLocaleString('ko-KR') }}명</dd>
          <dt>등급이 바뀐 고객</dt>
          <dd>{{ runResult.changedCount }}명</dd>
          <dt>이력 남김</dt>
          <dd>{{ runResult.historyCreated }}건</dd>
          <dt>걸린 시간</dt>
          <dd>{{ (runResult.elapsedMs / 1000).toFixed(1) }}초</dd>
        </dl>
      </template>
    </section>
  </div>
</template>

<style scoped>
.set__tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-border);
  margin: -4px 0 16px;
  overflow-x: auto;
}
.set__tab {
  flex: 0 0 auto;
  padding: 10px 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  font: inherit;
  color: var(--color-text-muted);
  cursor: pointer;
}
.set__tab--on {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.set__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
  margin: 12px 0;
}
.set__h {
  font-size: var(--font-size-sm);
  margin-bottom: 8px;
}
.set__row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.set__score {
  width: 100px;
}
.set__name {
  width: 180px;
}
.set__unit {
  width: 80px;
}
.set__rules {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.set__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin: 12px 0;
}
.set__narrow {
  max-width: 360px;
}
.set__result {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 16px;
  margin: 16px 0 0;
}
.set__result dt {
  color: var(--color-text-muted);
}
.set__result dd {
  margin: 0;
}
</style>

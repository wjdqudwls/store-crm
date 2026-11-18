<script setup>
import { computed, onMounted, ref } from 'vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import GradeBadge from '@/components/GradeBadge.vue'
import ContactResultDialog from '@/components/ContactResultDialog.vue'
import { completeVisit, getToday, skipFollowUp } from '@/api/customers'
import { useSessionStore } from '@/stores/session'
import { useNotificationStore } from '@/stores/notifications'

/**
 * 오늘 할 일 `/today` (05 SCR-07, CAL-06). 밀린 할 일이 먼저 (BR-14).
 * 매장 계정: 매장 할 일 묶음 → 직원 할 일. 직원: 내 할 일만 (W3, R1).
 */
const router = useRouter()
const session = useSessionStore()
const notices = useNotificationStore()

const list = ref(null)
const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    list.value = (await getToday()).content
  })
onMounted(() => {
  load()
  if (!session.isStore && !session.isHq) notices.refresh()
})

const overdue = computed(() => (list.value ?? []).filter((t) => t.overdueDays > 0).length)
const groups = computed(() => {
  const all = list.value ?? []
  if (!session.isStore) return [{ key: 'mine', title: null, items: all }]
  return [
    { key: 'store', title: '매장 할 일', items: all.filter((t) => t.assignee === 'STORE') },
    { key: 'staff', title: '직원 할 일', items: all.filter((t) => t.assignee !== 'STORE') },
  ].filter((g) => g.items.length)
})

function soldLabel(iso) {
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()} 구매`
}

function openCustomer(t) {
  router.push({ path: `/customers/${t.customerId}`, query: { from: 'today' } })
}

const dialogOpen = ref(false)
const dialogTask = ref(null)
function openResult(t) {
  dialogTask.value = t
  dialogOpen.value = true
}

/** 방문 예약 할 일: 고객이 왔으면 [방문함]으로 끝낸다 (CAL-10) */
async function visited(t) {
  try {
    await completeVisit(t.id)
    ElMessage.success(`${t.customerName}님 방문을 확인했습니다`)
  } catch (e) {
    ElMessage.warning(
      e?.status === 409 ? '이미 처리된 할 일입니다' : (e?.body?.message ?? '다시 눌러 주세요'),
    )
  }
  load()
}

async function skip(t) {
  try {
    await ElMessageBox.confirm('이 할 일을 건너뛸까요?', '건너뛰기', {
      confirmButtonText: '건너뛰기',
      cancelButtonText: '돌아가기',
    })
  } catch {
    return
  }
  try {
    await skipFollowUp(t.id)
    ElMessage.success('건너뛰었습니다')
  } catch (e) {
    ElMessage.warning(
      e?.status === 409 ? '이미 처리된 할 일입니다' : (e?.body?.message ?? '다시 눌러 주세요'),
    )
  }
  load()
}
</script>

<template>
  <div class="page page--narrow">
    <header class="page__head">
      <h1 class="page__title">오늘 할 일</h1>
    </header>

    <p v-if="list" class="today__count">
      오늘 할 일 <strong>{{ list.length }}</strong>
      <template v-if="overdue">
        · <span class="today__overdue">밀린 할 일 {{ overdue }}</span>
      </template>
    </p>

    <LoadError :error="error" :busy="retrying" @retry="load" />
    <el-skeleton v-if="!list && !error" animated :rows="4" class="card" />
    <p v-else-if="list && !list.length" class="card empty">오늘 할 일이 없습니다.</p>

    <section v-for="g in groups" v-else-if="list" :key="g.key" class="today__group">
      <h2 v-if="g.title" class="today__group-title">{{ g.title }} {{ g.items.length }}</h2>
      <article v-for="t in g.items" :key="t.id" class="tcard card">
        <button type="button" class="tcard__body" @click="openCustomer(t)">
          <span class="tcard__who">
            <strong>{{ t.customerName }}</strong>
            <GradeBadge :grade="t.grade" />
            <span class="muted">{{ t.phoneMasked }}</span>
          </span>
          <span class="tcard__why">
            <el-tag v-if="t.kind === 'VISIT'" size="small" type="danger" effect="light">
              방문 예정
            </el-tag>
            <el-tag v-else-if="t.kind === 'RECALL'" size="small" type="warning" effect="light">
              다시 연락
            </el-tag>
            <template v-if="t.kind === 'SALE'">{{ t.stage }} · {{ soldLabel(t.soldAt) }}</template>
            <template v-else>{{ t.noteContent }}</template>
            <el-tag v-if="t.promotional" size="small" type="warning" effect="plain">홍보</el-tag>
          </span>
          <span v-if="t.promotional && !t.marketingAllowed" class="tcard__nopromo">
            홍보 연락 불가 · 안부 연락만
          </span>
          <span class="tcard__due">
            <span v-if="t.overdueDays" class="today__overdue">{{ t.overdueDays }}일 밀림</span>
            <span v-else>오늘</span>
            <span v-if="session.isStore && t.assignee !== 'STORE'" class="muted">
              · {{ t.staffName }}
            </span>
          </span>
        </button>
        <div class="tcard__btns">
          <el-button v-if="t.kind === 'VISIT'" type="primary" @click="visited(t)">방문함</el-button>
          <el-button v-else type="primary" @click="openResult(t)">연락 결과</el-button>
          <el-button @click="skip(t)">건너뛰기</el-button>
        </div>
      </article>
    </section>

    <ContactResultDialog v-model="dialogOpen" :task="dialogTask" @saved="load" />
  </div>
</template>

<style scoped>
.today__count {
  margin: 0;
}
.today__overdue {
  color: var(--color-danger);
  font-weight: var(--font-weight-semibold);
}
.today__group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.today__group-title {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-top: 8px;
}
.tcard {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tcard__body {
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
.tcard__who,
.tcard__why {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.tcard__why {
  font-size: var(--font-size-sm);
}
.tcard__nopromo {
  color: var(--color-warning);
  font-size: var(--font-size-sm);
}
.tcard__due {
  font-size: var(--font-size-sm);
}
.tcard__btns {
  display: flex;
  gap: 8px;
}
.tcard__btns .el-button {
  flex: 1;
  margin: 0;
}
</style>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SalesHistory from '@/components/sale/SalesHistory.vue'
import {
  createAccount,
  deactivateAccount,
  getStore,
  getStoreAccounts,
  handoverCandidates,
  resetPassword,
} from '@/api/stores'
import { getDayCloses, reopenDay } from '@/api/sales'
import { dateTimeLabel } from '@/features/customer/customerDetail'

/**
 * 매장 상세 `/hq/stores/{storeId}?tab=` (05 SCR-08, W10). 탭: 판매 내역 · 하루 마감 · 직원 · 매장 계정.
 */
const route = useRoute()
const router = useRouter()
const storeId = computed(() => Number(route.params.id))

const store = ref(null)
const closes = ref([])
const accounts = ref([])

async function load() {
  store.value = await getStore(storeId.value)
  ;[closes.value, accounts.value] = await Promise.all([
    getDayCloses(storeId.value),
    getStoreAccounts(storeId.value),
  ])
}
watch(storeId, load, { immediate: true })

const tabs = { sales: '판매 내역', 'day-closes': '하루 마감', staff: '직원', account: '매장 계정' }
const tab = computed(() => (tabs[route.query.tab] ? route.query.tab : 'sales'))
const selectTab = (t) => router.replace({ query: { tab: t } })

const roleLabels = {
  STORE: '매장 계정',
  MANAGER: '매니저',
  SENIOR: '시니어',
  JUNIOR: '주니어',
  STAFF: '스태프',
  KIOSK: '키오스크',
}
const slotLabels = { MAIN: '메인', TEMP: '임시' }
const people = computed(() => accounts.value.filter((a) => a.role !== 'STORE'))
const storeAccount = computed(() => accounts.value.find((a) => a.role === 'STORE'))

// ---- 하루 마감: 오늘이 마감이면 [다시 열기] ----
const todayKey = new Date().toLocaleDateString('sv-SE')
async function reopen(row) {
  try {
    await ElMessageBox.confirm(`${row.date} 판매 마감을 다시 열까요?`, '다시 열기', {
      confirmButtonText: '다시 열기',
      cancelButtonText: '돌아가기',
    })
  } catch {
    return
  }
  await reopenDay(storeId.value, row.date)
  ElMessage.success('마감을 다시 열었습니다')
  load()
}

// ---- 직원 등록 ----
const addOpen = ref(false)
const addForm = reactive({
  name: '',
  role: 'STAFF',
  managerSlot: 'MAIN',
  loginId: '',
  password: '',
})
const addError = ref('')
function openAdd() {
  Object.assign(addForm, {
    name: '',
    role: 'STAFF',
    managerSlot: 'MAIN',
    loginId: '',
    password: '',
  })
  addError.value = ''
  addOpen.value = true
}
async function saveAdd() {
  if (!addForm.name.trim() || !addForm.loginId.trim())
    return (addError.value = '이름과 로그인 ID를 넣어 주세요')
  try {
    await resetPassword(null, addForm.password)
    await createAccount(storeId.value, { ...addForm, name: addForm.name.trim() })
    addOpen.value = false
    ElMessage.success(`${addForm.name}을(를) 등록했습니다`)
    load()
  } catch (e) {
    addError.value = e?.body?.message ?? '등록됐는지 확인하지 못했습니다'
  }
}

// ---- 비활성화 (BR-15) ----
const offOpen = ref(false)
const offTarget = ref(null)
const offForm = reactive({ handoverTo: null, storePassword: '' })
const offError = ref('')
const candidates = computed(() =>
  offTarget.value ? handoverCandidates(storeId.value, offTarget.value.id) : [],
)
function openOff(a) {
  offTarget.value = a
  Object.assign(offForm, { handoverTo: null, storePassword: '' })
  offError.value = ''
  offOpen.value = true
}
async function saveOff() {
  const to = candidates.value.find((c) => c.id === offForm.handoverTo)
  if (!to) return (offError.value = '넘겨받을 사람을 골라 주세요')
  try {
    await resetPassword(storeAccount.value?.id, offForm.storePassword)
  } catch (e) {
    return (offError.value = e.body.message)
  }
  try {
    await ElMessageBox.confirm(
      `${offTarget.value.name} 님을 비활성화하고 담당 고객과 할 일을 ${to.name} 님께 넘길까요? 매장 계정 비밀번호도 바뀝니다.`,
      '비활성화',
      { confirmButtonText: '비활성화', cancelButtonText: '돌아가기', type: 'warning' },
    )
  } catch {
    return
  }
  const res = await deactivateAccount(storeId.value, offTarget.value.id, offForm)
  offOpen.value = false
  ElMessage.success(`고객 ${res.customerCount}명, 할 일 ${res.taskCount}건을 넘겼습니다`)
  load()
}

// ---- 비밀번호 다시 정하기 · QR ----
const pwOpen = ref(false)
const pwTarget = ref(null)
const pwValue = ref('')
const pwError = ref('')
function openPw(a) {
  pwTarget.value = a
  pwValue.value = ''
  pwError.value = ''
  pwOpen.value = true
}
async function savePw() {
  try {
    await resetPassword(pwTarget.value.id, pwValue.value)
    pwOpen.value = false
    ElMessage.success('비밀번호를 다시 정했습니다. 그 계정의 로그인이 모두 끊깁니다')
  } catch (e) {
    pwError.value = e.body.message
  }
}

const qrOpen = ref(false)
const qrUrl = ref('')
function openQr(a) {
  const code = store.value.code
  qrUrl.value = `${location.origin}/join?store=${code}${a?.staffCode ? `&staff=${a.staffCode}` : ''}`
  qrOpen.value = true
}
</script>

<template>
  <div class="page">
    <el-button link class="st__back" @click="router.push('/hq/stores')">‹ 매장 목록</el-button>
    <el-skeleton v-if="!store" animated :rows="4" class="card" />
    <template v-else>
      <header class="page__head">
        <h1 class="page__title">
          {{ store.name }} <span class="muted">{{ store.code }}</span>
        </h1>
      </header>

      <section class="card">
        <nav class="st__tabs" role="tablist">
          <button
            v-for="(label, t) in tabs"
            :key="t"
            type="button"
            role="tab"
            class="st__tab"
            :class="{ 'st__tab--on': tab === t }"
            :aria-selected="tab === t"
            @click="selectTab(t)"
          >
            {{ label }}
          </button>
        </nav>

        <SalesHistory v-if="tab === 'sales'" :store-id="storeId" />

        <template v-else-if="tab === 'day-closes'">
          <p class="muted">지난 날 판매는 판매 내역에서 정정합니다.</p>
          <el-table :data="closes">
            <el-table-column prop="date" label="날짜" width="120" />
            <el-table-column label="마감" min-width="160">
              <template #default="{ row }">
                {{ dateTimeLabel(row.closedAt, new Date(), true) }} · {{ row.closedByName }}
              </template>
            </el-table-column>
            <el-table-column label="다시 연 기록" min-width="160">
              <template #default="{ row }">
                <template v-if="row.reopenedAt">
                  {{ dateTimeLabel(row.reopenedAt, new Date(), true) }} · {{ row.reopenedByName }}
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column width="110">
              <template #default="{ row }">
                <el-button
                  v-if="row.date === todayKey && !row.reopenedAt"
                  size="small"
                  @click="reopen(row)"
                >
                  다시 열기
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-else-if="tab === 'staff'">
          <div class="st__bar">
            <el-button type="primary" @click="openAdd">직원 등록</el-button>
          </div>
          <el-table :data="people">
            <el-table-column prop="name" label="이름" min-width="120" />
            <el-table-column label="직급" width="120">
              <template #default="{ row }">
                {{ roleLabels[row.role] }}
                <template v-if="row.managerSlot"> · {{ slotLabels[row.managerSlot] }}</template>
              </template>
            </el-table-column>
            <el-table-column prop="loginId" label="로그인 ID" min-width="140" />
            <el-table-column label="상태" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.active ? 'success' : 'info'">
                  {{ row.active ? '활성' : '비활성' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="" min-width="260">
              <template #default="{ row }">
                <template v-if="row.active">
                  <el-button
                    v-if="row.role !== 'KIOSK'"
                    size="small"
                    link
                    type="danger"
                    @click="openOff(row)"
                  >
                    비활성화
                  </el-button>
                  <el-button size="small" link @click="openPw(row)">비밀번호 다시 정하기</el-button>
                  <el-button v-if="row.staffCode" size="small" link @click="openQr(row)"
                    >QR 받기</el-button
                  >
                </template>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-else-if="tab === 'account'">
          <div v-if="storeAccount" class="ftable st__acc">
            <div class="ft-label">매장 계정</div>
            <div class="ft-value">{{ storeAccount.name }}</div>
            <div class="ft-label">로그인 ID</div>
            <div class="ft-value">{{ storeAccount.loginId }}</div>
          </div>
          <div class="st__bar st__bar--left">
            <el-button @click="openPw(storeAccount)">매장 계정 비밀번호 다시 정하기</el-button>
            <el-button @click="openQr(null)">매장 QR 받기</el-button>
          </div>
        </template>
      </section>
    </template>

    <!-- 직원 등록 -->
    <el-dialog v-model="addOpen" title="직원 등록" width="min(480px, 94vw)" align-center>
      <el-form label-position="top">
        <el-form-item label="이름"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="직급">
          <el-select v-model="addForm.role">
            <el-option
              v-for="r in ['MANAGER', 'SENIOR', 'JUNIOR', 'STAFF', 'KIOSK']"
              :key="r"
              :label="roleLabels[r]"
              :value="r"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="addForm.role === 'MANAGER'" label="매니저 자리">
          <el-radio-group v-model="addForm.managerSlot">
            <el-radio-button value="MAIN">메인</el-radio-button>
            <el-radio-button value="TEMP">임시</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="로그인 ID"
          ><el-input v-model="addForm.loginId" placeholder="gn01-이름"
        /></el-form-item>
        <el-form-item label="처음 비밀번호 (8자 이상, 영문과 숫자)">
          <el-input
            v-model="addForm.password"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
      </el-form>
      <p v-if="addError" class="field-error">{{ addError }}</p>
      <template #footer>
        <el-button @click="addOpen = false">돌아가기</el-button>
        <el-button type="primary" @click="saveAdd">등록</el-button>
      </template>
    </el-dialog>

    <!-- 비활성화 -->
    <el-dialog v-model="offOpen" title="비활성화" width="min(480px, 94vw)" align-center>
      <el-form v-if="offTarget" label-position="top">
        <p class="muted">{{ offTarget.name }} 님의 담당 고객과 남은 할 일을 넘깁니다.</p>
        <el-form-item label="넘겨받을 사람">
          <el-select v-model="offForm.handoverTo" placeholder="같은 직급 이상">
            <el-option
              v-for="c in candidates"
              :key="c.id"
              :label="`${c.name} · ${roleLabels[c.role]}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="매장 계정 새 비밀번호 (8자 이상, 영문과 숫자)">
          <el-input
            v-model="offForm.storePassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
      </el-form>
      <p v-if="offError" class="field-error">{{ offError }}</p>
      <template #footer>
        <el-button @click="offOpen = false">돌아가기</el-button>
        <el-button type="danger" @click="saveOff">비활성화</el-button>
      </template>
    </el-dialog>

    <!-- 비밀번호 다시 정하기 -->
    <el-dialog v-model="pwOpen" title="비밀번호 다시 정하기" width="min(420px, 94vw)" align-center>
      <p v-if="pwTarget" class="muted">{{ pwTarget.name }} · {{ pwTarget.loginId }}</p>
      <el-input
        v-model="pwValue"
        type="password"
        show-password
        placeholder="8자 이상, 영문과 숫자"
        autocomplete="new-password"
      />
      <p v-if="pwError" class="field-error">{{ pwError }}</p>
      <template #footer>
        <el-button @click="pwOpen = false">돌아가기</el-button>
        <el-button type="primary" @click="savePw">저장</el-button>
      </template>
    </el-dialog>

    <!-- QR -->
    <el-dialog v-model="qrOpen" title="QR 받기" width="min(420px, 94vw)" align-center>
      <p class="draft-note">
        초안: QR 이미지는 백엔드(ZXing, B1-4)에서 만듭니다. 지금은 주소만 보여 줍니다.
      </p>
      <p class="st__qr">{{ qrUrl }}</p>
    </el-dialog>
  </div>
</template>

<style scoped>
.st__back {
  align-self: flex-start;
  padding-left: 0;
}
.st__tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-border);
  margin: -4px 0 16px;
  overflow-x: auto;
}
.st__tab {
  flex: 0 0 auto;
  padding: 10px 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  font: inherit;
  color: var(--color-text-muted);
  cursor: pointer;
}
.st__tab--on {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.st__bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}
.st__bar--left {
  justify-content: flex-start;
}
.st__acc {
  margin-bottom: 16px;
}
.st__qr {
  word-break: break-all;
  font-family: ui-monospace, monospace;
  font-size: var(--font-size-sm);
}
</style>

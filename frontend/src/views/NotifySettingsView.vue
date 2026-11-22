<script setup>
import { onMounted, ref } from 'vue'
import LoadError from '@/components/LoadError.vue'
import { useLoadError } from '@/composables/useLoadError'
import { ElMessage } from 'element-plus'
import { getNotifySetting, saveNotifySetting } from '@/api/notifications'
import { timingLabels } from '@/features/customer/reminder'
import { isFake } from '@/api/mode'
import { apiMessage } from '@/utils/apiMessage'

/**
 * 내 알림 설정 `/me/notifications` (01 CAL-12·13, 직원만).
 * 방문 예약·재연락 알림을 언제 받을지, 이 폰에서 푸시 알림을 받을지 고른다.
 * 실제 푸시는 웹 푸시(서비스 워커 + 구독 저장)로 서버가 보낸다. 초안에서는 브라우저 알림 권한과 시험 알림만 한다.
 */
const timing = ref('DAY_BEFORE')
const push = ref(false)
const loaded = ref(false)
const permission = ref(
  typeof Notification === 'undefined' ? 'unsupported' : Notification.permission,
)

const { error, retrying, run } = useLoadError()
const load = () =>
  run(async () => {
    const s = await getNotifySetting()
    timing.value = s.timing
    push.value = s.push
    loaded.value = true
  })
onMounted(load)

async function save() {
  try {
    await saveNotifySetting({ timing: timing.value, push: push.value })
    ElMessage.success('알림 설정을 저장했습니다')
  } catch (e) {
    ElMessage.error(apiMessage(e, '저장됐는지 확인하지 못했습니다. 다시 눌러 주세요'))
    load() // 화면 값을 서버 값으로 되돌린다
  }
}

async function togglePush(on) {
  // 실서버: 폰 푸시 구독은 서비스 워커·VAPID 키가 준비된 뒤에 연결한다 (웹 푸시 작업). 지금은 알림함에서만 받는다
  if (!isFake) {
    push.value = false
    ElMessage.info('폰 푸시 알림은 준비 중입니다. 알림은 알림함에서 확인할 수 있어요')
    return
  }
  if (on && permission.value !== 'granted') {
    if (permission.value === 'unsupported') {
      ElMessage.warning(
        '이 브라우저는 알림을 지원하지 않습니다. 아이폰은 홈 화면에 추가한 뒤 켤 수 있어요',
      )
      push.value = false
      return
    }
    permission.value = await Notification.requestPermission()
    if (permission.value !== 'granted') {
      ElMessage.warning('알림 권한을 허용해야 받을 수 있어요')
      push.value = false
      return
    }
  }
  save()
}

function testPush() {
  new Notification('CRM pos', { body: '내일 김하늘님 방문 예정이에요.' })
}

const timingOptions = Object.entries(timingLabels)
</script>

<template>
  <div class="page page--narrow">
    <header class="page__head">
      <h1 class="page__title">알림 설정</h1>
    </header>

    <LoadError :error="error" :busy="retrying" @retry="load" />

    <div v-if="loaded" class="card">
      <div class="ftable ftable--2">
        <div class="ft-caption">방문 예약 · 다시 연락 알림</div>
        <div class="ft-label">알림 시점</div>
        <div class="ft-value">
          <el-radio-group v-model="timing" @change="save">
            <el-radio-button v-for="[v, l] in timingOptions" :key="v" :value="v">{{
              l
            }}</el-radio-button>
          </el-radio-group>
          <p class="muted">
            아침 9시에 보냅니다. 같은 날 고객이 여럿이면 "내일 고객님들이 방문할 예정이에요."처럼
            하나로 묶습니다.
          </p>
        </div>
        <div class="ft-label">폰 푸시 알림</div>
        <div class="ft-value">
          <div class="ft-inline">
            <el-switch v-model="push" active-text="이 폰에서 받기" @change="togglePush" />
            <span v-if="!isFake" class="muted">준비 중</span>
            <el-button v-if="push && permission === 'granted'" size="small" @click="testPush">
              시험 알림 보내기
            </el-button>
          </div>
          <p class="muted">
            앱을 열지 않아도 폰에 알림이 울립니다. 아이폰은 Safari에서 [공유] → [홈 화면에 추가]를
            한 뒤 켤 수 있어요.
          </p>
        </div>
      </div>
      <p class="draft-note pw__note">
        초안: 서버에서 푸시를 보내는 일(웹 푸시 구독 저장, 아침 9시 스케줄러)은 백엔드 B4 작업에서
        만듭니다. 지금은 알림함에만 쌓입니다.
      </p>
    </div>
  </div>
</template>

<style scoped>
.pw__note {
  margin-top: 12px;
}
</style>

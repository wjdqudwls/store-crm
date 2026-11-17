<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SalesHistory from '@/components/sale/SalesHistory.vue'
import { closeDay, getDailySummary } from '@/api/sales'
import { dateTimeLabel } from '@/features/customer/customerDetail'

/**
 * 판매 내역 `/sales` (05 SCR-03, SAL-05·08). 매장 계정의 자기 매장.
 * 오른쪽 위에 오늘 마감 상태와 [오늘 판매 마감] (W8).
 */
const summary = ref(null)
const historyRef = ref()

async function loadSummary() {
  summary.value = await getDailySummary()
}
onMounted(loadSummary)

async function close() {
  try {
    await ElMessageBox.confirm(
      `오늘 판매 ${summary.value.saleCount}건을 마감할까요? 마감 뒤에는 본사만 다시 열 수 있습니다.`,
      '오늘 판매 마감',
      { confirmButtonText: '마감', cancelButtonText: '돌아가기', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await closeDay()
    ElMessage.success('오늘 판매를 마감했습니다')
    loadSummary()
    historyRef.value?.load()
  } catch (e) {
    ElMessage.error(e?.body?.message ?? '마감됐는지 확인하지 못했습니다')
  }
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">판매 내역</h1>
      <div v-if="summary" class="page__actions">
        <el-tag v-if="summary.closed" type="info" size="large">
          마감됨 · {{ dateTimeLabel(summary.closedAt).replace('오늘 ', '') }}
        </el-tag>
        <template v-else>
          <el-tag type="primary" effect="plain" size="large">
            오늘 {{ summary.saleCount }}건 · 마감 전
          </el-tag>
          <el-button type="primary" @click="close">오늘 판매 마감</el-button>
        </template>
      </div>
    </header>
    <section class="card">
      <SalesHistory ref="historyRef" />
    </section>
  </div>
</template>

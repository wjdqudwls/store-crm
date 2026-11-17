<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStores } from '@/api/stores'
import { getDailySummaries } from '@/api/sales'
import { dateTimeLabel } from '@/features/customer/customerDetail'

/**
 * 매장 목록 `/hq/stores` (05 SCR-08, ADM-01). 본사의 첫 화면.
 * 매장 목록과 오늘 판매 요약을 매장 ID로 합친다 (W10).
 */
const router = useRouter()
const list = ref(null)

onMounted(async () => {
  const [stores, summaries] = await Promise.all([getStores(), getDailySummaries()])
  list.value = stores.map((s) => ({ ...s, today: summaries.find((x) => x.storeId === s.id) }))
})
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">매장 목록</h1>
    </header>
    <section class="card">
      <el-skeleton v-if="!list" animated :rows="3" />
      <el-table
        v-else
        :data="list"
        row-class-name="clickable-row"
        @row-click="(s) => router.push(`/hq/stores/${s.id}`)"
      >
        <el-table-column prop="code" label="코드" width="90" />
        <el-table-column prop="name" label="매장" min-width="120" />
        <el-table-column prop="activeStaffCount" label="활성 직원" width="100" align="right">
          <template #default="{ row }">{{ row.activeStaffCount }}명</template>
        </el-table-column>
        <el-table-column label="오늘 판매" width="110" align="right">
          <template #default="{ row }">{{ row.today.saleCount }}건</template>
        </el-table-column>
        <el-table-column label="오늘 마감" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.today.closed" type="info" size="small">
              마감됨 · {{ dateTimeLabel(row.today.closedAt).replace('오늘 ', '') }}
            </el-tag>
            <el-tag v-else size="small" effect="plain">마감 전</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

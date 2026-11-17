<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getProducts, importProducts } from '@/api/products'
import { formatWon, itemTypeLabels, sizeLabel, targetLabels } from '@/utils/format'

/**
 * 상품 관리 `/products` (05 SCR-05, PRD-01·02). 매장 계정·본사.
 * 상품을 등록하지 않아도 판매 기록에서 직접 입력할 수 있다 (W9).
 */
const router = useRouter()
const keyword = ref('')
const list = ref(null)

async function load() {
  list.value = (await getProducts(keyword.value)).content
}
onMounted(load)

const colorsOf = (p) => [...new Set(p.skus.map((s) => s.color).filter(Boolean))].join(', ') || '-'
const sizesOf = (p) =>
  [...new Set(p.skus.map((s) => s.size))].map((s) => sizeLabel(s, p.itemType, p.target)).join(', ')

// ---- 엑셀 올리기 (초안: 실패 예시) ----
const importOpen = ref(false)
const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
function onFile(file) {
  importFile.value = file
  importResult.value = null
}
async function upload() {
  importing.value = true
  try {
    const res = await importProducts(importFile.value)
    importResult.value = { ok: true, message: `${res.importedCount}개 상품을 등록했습니다` }
    load()
  } catch (e) {
    importResult.value = {
      ok: false,
      message: e.body.message,
      errors: e.body.details?.errors ?? [],
    }
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">상품</h1>
      <div class="page__actions">
        <el-button type="primary" plain @click="load">조회</el-button>
        <el-button @click="importOpen = true">엑셀 올리기</el-button>
        <el-button type="primary" @click="router.push('/products/new')">상품 등록</el-button>
      </div>
    </header>

    <form class="ftable" @submit.prevent="load">
      <label class="ft-label" for="prod-keyword">품번·상품명</label>
      <div class="ft-value ft-wide">
        <el-input
          id="prod-keyword"
          v-model="keyword"
          placeholder="품번 또는 상품명 입력 후 Enter"
          clearable
          class="prod__keyword"
          @clear="load"
        />
      </div>
    </form>

    <section class="card">
      <el-skeleton v-if="!list" animated :rows="5" />
      <p v-else-if="!list.length" class="empty">
        {{
          keyword
            ? `'${keyword}' 상품이 없습니다.`
            : '등록한 상품이 없습니다. 상품을 등록하지 않아도 판매 기록에서 직접 입력할 수 있습니다.'
        }}
      </p>
      <el-table
        v-else
        :data="list"
        row-class-name="clickable-row"
        @row-click="(p) => router.push(`/products/${p.id}`)"
      >
        <el-table-column label="품번" width="100">
          <template #default="{ row }">{{ row.productCode ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="상품명" min-width="160" />
        <el-table-column label="품목" width="90">
          <template #default="{ row }">{{ itemTypeLabels[row.itemType] }}</template>
        </el-table-column>
        <el-table-column label="대상" width="80">
          <template #default="{ row }">{{ targetLabels[row.target] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="정가" width="120" align="right">
          <template #default="{ row }">{{ formatWon(row.listPrice) }}</template>
        </el-table-column>
        <el-table-column label="색상" min-width="120">
          <template #default="{ row }">{{ colorsOf(row) }}</template>
        </el-table-column>
        <el-table-column label="사이즈" min-width="160">
          <template #default="{ row }">{{ sizesOf(row) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="importOpen" title="엑셀 올리기" width="min(560px, 94vw)" align-center>
      <div class="imp">
        <p class="muted">
          엑셀 폼에 한 행에 상품 하나, 사이즈·색상은 쉼표로 여러 개 적습니다. 잘못된 행이 하나라도
          있으면 아무것도 저장하지 않습니다.
        </p>
        <p class="draft-note">
          초안: 파일 읽기는 백엔드(B1-2)에서 만듭니다. 지금은 실패 예시를 보여 줍니다.
        </p>
        <div class="imp__row">
          <el-button link type="primary">엑셀 폼 내려받기</el-button>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".xlsx"
            :on-change="onFile"
          >
            <el-button>파일 고르기</el-button>
          </el-upload>
          <span class="muted">{{ importFile?.name ?? '고른 파일 없음' }}</span>
        </div>
        <template v-if="importResult">
          <p :class="importResult.ok ? 'imp__ok' : 'field-error'">{{ importResult.message }}</p>
          <el-table v-if="importResult.errors?.length" :data="importResult.errors" size="small">
            <el-table-column prop="row" label="행" width="60" />
            <el-table-column prop="column" label="열" width="80" />
            <el-table-column prop="reason" label="이유" />
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="importOpen = false">닫기</el-button>
        <el-button type="primary" :disabled="!importFile" :loading="importing" @click="upload">
          올리기
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.prod__keyword {
  max-width: 420px;
}
.imp {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.imp p {
  margin: 0;
}
.imp__row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.imp__ok {
  color: var(--color-success);
}
</style>

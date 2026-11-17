<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChoiceButtons from '@/components/ChoiceButtons.vue'
import { createProduct, getProduct, updateProduct } from '@/api/products'
import { getSizes } from '@/api/public'
import { itemTypeLabels, sizeLabel, targetLabels } from '@/utils/format'

/**
 * 상품 등록·수정 `/products/new`, `/products/{id}` (05 SCR-05, PRD-01).
 * 사이즈는 품목·대상의 허용값만 (02 3.2). 대상이 없으면 그 품목의 모든 허용값.
 */
const route = useRoute()
const router = useRouter()
const editId = computed(() => (route.params.id ? Number(route.params.id) : null))

const form = reactive({
  name: '',
  itemType: null,
  target: null,
  sizes: [],
  listPrice: null,
  productCode: '',
  colors: [],
})
const colorInput = ref('')
const errors = ref({})
const saving = ref(false)
const sizeTable = ref([])
const loaded = ref(!editId.value)

onMounted(async () => {
  sizeTable.value = await getSizes()
  if (editId.value) {
    const p = await getProduct(editId.value)
    Object.assign(form, {
      name: p.name,
      itemType: p.itemType,
      target: p.target,
      sizes: [...new Set(p.skus.map((s) => s.size))],
      listPrice: p.listPrice,
      productCode: p.productCode ?? '',
      colors: [...new Set(p.skus.map((s) => s.color).filter(Boolean))],
    })
    await Promise.resolve()
    loaded.value = true
  }
})

const sizeOptions = computed(() => {
  if (!form.itemType) return []
  const rows = sizeTable.value.filter((r) => r.itemType === form.itemType)
  const t = form.target === 'UNISEX' ? 'MEN' : form.target
  const pick = t ? rows.filter((r) => r.target === t || r.target === null) : rows
  const list = [...new Set(pick.flatMap((r) => r.sizes))]
  return list.map((s) => ({ value: s, label: sizeLabel(s, form.itemType, form.target) }))
})
// 품목·대상이 바뀌면 허용값이 아닌 사이즈를 뺀다
watch(
  () => [form.itemType, form.target],
  () => {
    if (!loaded.value) return
    const ok = new Set(sizeOptions.value.map((o) => o.value))
    form.sizes = form.sizes.filter((s) => ok.has(s))
  },
)

function addColor() {
  const c = colorInput.value.trim()
  if (c && !form.colors.includes(c)) form.colors.push(c)
  colorInput.value = ''
}

const skuCount = computed(() => Math.max(form.colors.length, 1) * form.sizes.length)
const itemOptions = Object.entries(itemTypeLabels).map(([value, label]) => ({ value, label }))
const targetOptions = Object.entries(targetLabels).map(([value, label]) => ({ value, label }))

async function save() {
  const e = {}
  if (!form.name.trim()) e.name = '상품명을 입력해 주세요'
  if (!form.itemType) e.itemType = '품목을 골라 주세요'
  if (!form.sizes.length) e.sizes = '사이즈를 1개 이상 골라 주세요'
  if (form.listPrice == null || form.listPrice < 0) e.listPrice = '0 이상 정수로 입력해 주세요'
  errors.value = e
  if (Object.keys(e).length) return
  saving.value = true
  try {
    const body = { ...form, name: form.name.trim(), productCode: form.productCode.trim() }
    if (editId.value) await updateProduct(editId.value, body)
    else await createProduct(body)
    ElMessage.success(editId.value ? '상품을 고쳤습니다' : '상품을 등록했습니다')
    router.push('/products')
  } catch (err) {
    if (err?.body?.code === 'PRODUCT_CODE_DUPLICATED')
      errors.value = { productCode: err.body.message }
    else ElMessage.error(err?.body?.message ?? '저장됐는지 확인하지 못했습니다')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page pf-page">
    <el-button link class="pf__back" @click="router.push('/products')">‹ 상품 목록</el-button>
    <header class="page__head">
      <h1 class="page__title">{{ editId ? '상품 수정' : '상품 등록' }}</h1>
      <div class="page__actions">
        <el-button @click="router.push('/products')">목록</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editId ? '저장' : '등록' }}
        </el-button>
      </div>
    </header>

    <form class="card" @submit.prevent="save">
      <p class="ft-legend"><b>빨간 글씨</b>는 필수 칸입니다.</p>
      <div class="ftable">
        <label class="ft-label is-required" for="pf-name">상품명</label>
        <div class="ft-value">
          <el-input
            id="pf-name"
            v-model="form.name"
            maxlength="100"
            placeholder="울 블렌드 롱코트"
          />
          <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
        </div>
        <label class="ft-label" for="pf-code">품번</label>
        <div class="ft-value">
          <el-input id="pf-code" v-model="form.productCode" maxlength="30" placeholder="D2110" />
          <p v-if="errors.productCode" class="field-error">{{ errors.productCode }}</p>
        </div>

        <div class="ft-label is-required">품목</div>
        <div class="ft-value">
          <ChoiceButtons v-model="form.itemType" :options="itemOptions" size="small" />
          <p v-if="errors.itemType" class="field-error">{{ errors.itemType }}</p>
        </div>
        <div class="ft-label">대상</div>
        <div class="ft-value">
          <ChoiceButtons v-model="form.target" :options="targetOptions" size="small" />
        </div>

        <label class="ft-label is-required" for="pf-price">정가</label>
        <div class="ft-value">
          <div class="ft-inline">
            <el-input-number
              id="pf-price"
              v-model="form.listPrice"
              :min="0"
              :step="1000"
              :controls="false"
            />
            <span class="muted">원</span>
          </div>
          <p v-if="errors.listPrice" class="field-error">{{ errors.listPrice }}</p>
        </div>
        <div class="ft-label">판매 단위</div>
        <div class="ft-value">
          <span>
            색상 {{ form.colors.length || '없음' }} × 사이즈 {{ form.sizes.length }} =
            <strong>{{ skuCount }}개</strong>
          </span>
        </div>

        <div class="ft-label is-required">사이즈<small>여러 개</small></div>
        <div class="ft-value ft-wide">
          <span v-if="!form.itemType" class="muted">품목을 먼저 고르세요</span>
          <ChoiceButtons v-else v-model="form.sizes" :options="sizeOptions" multiple size="small" />
          <p v-if="errors.sizes" class="field-error">{{ errors.sizes }}</p>
        </div>

        <div class="ft-label">색상<small>여러 개</small></div>
        <div class="ft-value ft-wide">
          <div class="ft-inline">
            <el-tag
              v-for="c in form.colors"
              :key="c"
              closable
              @close="form.colors = form.colors.filter((x) => x !== c)"
            >
              {{ c }}
            </el-tag>
            <el-input
              v-model="colorInput"
              placeholder="색상 입력 후 Enter"
              class="pf__color-input"
              @keydown.enter.prevent="addColor"
            />
          </div>
        </div>
      </div>
      <p v-if="editId" class="muted pf__note">사이즈·색상을 빼도 과거 판매 기록은 그대로입니다.</p>
    </form>
  </div>
</template>

<style scoped>
.pf-page {
  max-width: 1000px;
}
.pf__back {
  align-self: flex-start;
  padding-left: 0;
}
.pf__color-input {
  width: 180px;
}
.pf__note {
  margin: 12px 0 0;
}
</style>

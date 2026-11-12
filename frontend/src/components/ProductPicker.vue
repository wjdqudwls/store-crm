<script setup>
import { computed, onMounted, ref } from 'vue'
import { searchProducts } from '@/api/sales'
import { getSizes } from '@/api/public'
import { useSaleRecentStore } from '@/stores/saleRecent'
import { formatWon, itemTypeLabels, targetLabels, sizeLabel } from '@/utils/format'

/**
 * 상품 고르기 (05 1.3, S5): 상품명·품번 검색 → 상품 → 색상 버튼 → 사이즈 버튼 → [담기].
 * "직접 입력"으로 바꾸면 상품명·품목·사이즈(필수), 색상·대상(선택).
 */
const emit = defineEmits(['add'])
const recent = useSaleRecentStore()

const mode = ref('registered')

// 등록 상품
const keyword = ref('')
const results = ref(null)
const product = ref(null)
const color = ref(null)
const size = ref(null)

async function search() {
  if (!keyword.value.trim()) return
  const res = await searchProducts(keyword.value)
  results.value = res.content
  if (res.content.length === 1) choose(res.content[0])
}

function choose(p) {
  product.value = p
  results.value = null
  const colors = [...new Set(p.skus.map((s) => s.color))]
  color.value = colors.length === 1 ? colors[0] : null
  size.value = null
}

const colors = computed(() =>
  product.value ? [...new Set(product.value.skus.map((s) => s.color))] : [],
)
const sizes = computed(() =>
  product.value ? product.value.skus.filter((s) => s.color === color.value).map((s) => s.size) : [],
)
const sku = computed(() =>
  product.value?.skus.find((s) => s.color === color.value && s.size === size.value),
)

function addRegistered() {
  if (!sku.value) return
  const p = product.value
  emit('add', {
    skuId: sku.value.skuId,
    productCode: p.productCode,
    productName: p.name,
    itemType: p.itemType,
    target: p.target,
    color: sku.value.color,
    size: sku.value.size,
    listPrice: p.listPrice,
  })
  recent.pickProduct(p)
  product.value = null
  keyword.value = ''
}

// 직접 입력
const sizeTable = ref([])
onMounted(async () => {
  sizeTable.value = await getSizes()
})
const direct = ref({ productName: '', itemType: null, target: null, color: '', size: null })
const directSizes = computed(() => {
  const { itemType, target } = direct.value
  if (!itemType) return []
  const rows = sizeTable.value.filter((r) => r.itemType === itemType)
  // 대상이 없으면 그 품목의 전체 허용값 (05 SCR-02)
  const pick = target
    ? rows.filter((r) => r.target === (target === 'UNISEX' ? 'MEN' : target) || r.target === null)
    : rows
  return [...new Set(pick.flatMap((r) => r.sizes))]
})
const directReady = computed(
  () => direct.value.productName.trim() && direct.value.itemType && direct.value.size,
)

function addDirect() {
  if (!directReady.value) return
  emit('add', {
    skuId: null,
    productCode: null,
    productName: direct.value.productName.trim(),
    itemType: direct.value.itemType,
    target: direct.value.target,
    color: direct.value.color.trim() || null,
    size: direct.value.size,
    listPrice: null,
  })
  direct.value = { productName: '', itemType: null, target: null, color: '', size: null }
}
</script>

<template>
  <div class="picker">
    <el-radio-group v-model="mode" size="small" class="picker__mode">
      <el-radio-button value="registered">등록 상품</el-radio-button>
      <el-radio-button value="direct">직접 입력</el-radio-button>
    </el-radio-group>

    <template v-if="mode === 'registered'">
      <el-input
        v-model="keyword"
        placeholder="상품명 또는 품번 입력 후 Enter"
        clearable
        @keydown.enter.prevent="search"
        @input="results = null"
      >
        <template #append><el-button @click="search">찾기</el-button></template>
      </el-input>

      <div v-if="!product && !results && recent.recentProducts.length" class="picker__recent">
        <span class="picker__hint">최근</span>
        <el-button v-for="p in recent.recentProducts" :key="p.id" size="small" @click="choose(p)">
          {{ p.name }}
        </el-button>
      </div>

      <ul v-if="results?.length" class="picker__list">
        <li v-for="p in results" :key="p.id" class="picker__item" @click="choose(p)">
          <span class="picker__code">{{ p.productCode }}</span>
          <span class="picker__name">{{ p.name }}</span>
          <span class="picker__hint"
            >{{ itemTypeLabels[p.itemType] }} · {{ targetLabels[p.target] }}</span
          >
          <span class="picker__price">{{ formatWon(p.listPrice) }}</span>
        </li>
      </ul>
      <div v-else-if="results" class="picker__empty">
        등록된 상품이 없습니다
        <el-button size="small" @click="mode = 'direct'">직접 입력</el-button>
      </div>

      <div v-if="product" class="picker__chosen">
        <div class="picker__chosen-head">
          <span class="picker__code">{{ product.productCode }}</span>
          <strong>{{ product.name }}</strong>
          <span class="picker__price">{{ formatWon(product.listPrice) }}</span>
          <el-button link @click="product = null">다시 찾기</el-button>
        </div>
        <div class="picker__row">
          <span class="picker__label">색상</span>
          <div class="picker__chips">
            <el-button
              v-for="c in colors"
              :key="c"
              :type="color === c ? 'primary' : 'default'"
              @click="((color = c), (size = null))"
            >
              {{ c }}
            </el-button>
          </div>
        </div>
        <div class="picker__row">
          <span class="picker__label">사이즈</span>
          <div class="picker__chips">
            <span v-if="!color" class="picker__hint">색상을 먼저 고르세요</span>
            <el-button
              v-for="s in sizes"
              :key="s"
              :type="size === s ? 'primary' : 'default'"
              @click="size = s"
            >
              {{ sizeLabel(s, product.itemType, product.target) }}
            </el-button>
          </div>
        </div>
        <el-button type="primary" :disabled="!sku" class="picker__add" @click="addRegistered"
          >담기</el-button
        >
      </div>
    </template>

    <div v-else class="picker__direct">
      <el-input v-model="direct.productName" placeholder="상품명 (필수)" maxlength="100" />
      <div class="picker__direct-row">
        <el-select v-model="direct.itemType" placeholder="품목 (필수)" @change="direct.size = null">
          <el-option
            v-for="(label, code) in itemTypeLabels"
            :key="code"
            :label="label"
            :value="code"
          />
        </el-select>
        <el-select
          v-model="direct.target"
          placeholder="대상 (선택)"
          clearable
          @change="direct.size = null"
        >
          <el-option
            v-for="(label, code) in targetLabels"
            :key="code"
            :label="label"
            :value="code"
          />
        </el-select>
        <el-input v-model="direct.color" placeholder="색상 (선택)" maxlength="30" />
      </div>
      <div class="picker__row">
        <span class="picker__label">사이즈</span>
        <div class="picker__chips">
          <span v-if="!direct.itemType" class="picker__hint">품목을 먼저 고르세요</span>
          <el-button
            v-for="s in directSizes"
            :key="s"
            :type="direct.size === s ? 'primary' : 'default'"
            @click="direct.size = s"
          >
            {{ sizeLabel(s, direct.itemType, direct.target) }}
          </el-button>
        </div>
      </div>
      <el-button type="primary" :disabled="!directReady" class="picker__add" @click="addDirect"
        >담기</el-button
      >
    </div>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.picker__mode {
  align-self: flex-start;
}
.picker__recent,
.picker__chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.picker__chips .el-button {
  margin: 0;
  min-width: 52px;
}
.picker__list {
  list-style: none;
  margin: 0;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
.picker__item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: var(--touch-min);
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
}
.picker__item:hover {
  background: var(--el-color-primary-light-9);
}
.picker__code {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.picker__name {
  font-weight: var(--font-weight-semibold);
}
.picker__price {
  margin-left: auto;
}
.picker__hint {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.picker__empty {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.picker__chosen,
.picker__direct {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-subtle);
}
.picker__chosen-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.picker__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.picker__label {
  flex: 0 0 48px;
  padding-top: 6px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}
.picker__direct-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.picker__add {
  align-self: flex-end;
  min-width: 96px;
}
@media (max-width: 767.98px) {
  .picker__direct-row {
    grid-template-columns: 1fr;
  }
  .picker__add {
    align-self: stretch;
  }
}
</style>

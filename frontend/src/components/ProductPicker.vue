<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { searchProducts } from '@/api/sales'
import { getSizes } from '@/api/public'
import { useSaleRecentStore } from '@/stores/saleRecent'
import { formatWon, itemTypeLabels, targetLabels, sizeLabel } from '@/utils/format'

/**
 * 상품 고르기 (05 1.3, S5): 상품명·품번 검색 → 상품 → 색상 버튼 → 사이즈 버튼 → [담기].
 * "직접 입력"으로 바꾸면 상품명·품목·사이즈(필수), 색상·대상(선택).
 */
const props = defineProps({
  /** 'stack': 폰(버튼으로 고르기) · 'row': PC, 기존 매장 POS처럼 라벨 칸 + 입력 칸 한 줄 */
  layout: { type: String, default: 'stack' },
})
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
  active.value = 0
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
  color.value = null
  size.value = null
  keyword.value = ''
  if (props.layout === 'row') nextTick(() => keywordRef.value?.focus())
}
const keywordRef = ref()

// PC 한 줄: 결과 목록에서 ↑↓ Enter로 고른다
const active = ref(0)
function onKeywordEnter() {
  if (results.value?.length) choose(results.value[active.value] ?? results.value[0])
  else search()
}
function moveResult(step) {
  const n = results.value?.length
  if (n) active.value = (active.value + step + n) % n
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
  <!-- PC: 기존 매장 POS처럼 라벨 칸 + 입력 칸 -->
  <div v-if="layout === 'row'" class="ftable prow">
    <div class="ft-label">상품 입력</div>
    <div class="ft-value ft-wide">
      <div class="ft-inline">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="registered">등록 상품</el-radio-button>
          <el-radio-button value="direct">직접 입력</el-radio-button>
        </el-radio-group>
        <template v-if="mode === 'registered' && recent.recentProducts.length">
          <span class="picker__hint">최근</span>
          <el-button
            v-for="p in recent.recentProducts"
            :key="p.id"
            size="small"
            text
            bg
            @click="choose(p)"
          >
            {{ p.name }}
          </el-button>
        </template>
      </div>
    </div>

    <template v-if="mode === 'registered'">
      <label class="ft-label is-required" for="pp-keyword">품번·상품명</label>
      <div class="ft-value prow__search">
        <div v-if="product" class="prow__chosen">
          <span class="picker__code">{{ product.productCode }}</span>
          <strong>{{ product.name }}</strong>
          <span class="picker__price">{{ formatWon(product.listPrice) }}</span>
          <el-button link size="small" @click="product = null">다시 찾기</el-button>
        </div>
        <el-input
          v-else
          id="pp-keyword"
          ref="keywordRef"
          v-model="keyword"
          placeholder="품번 또는 상품명 입력 후 Enter"
          clearable
          @keydown.enter.prevent="onKeywordEnter"
          @keydown.down.prevent="moveResult(1)"
          @keydown.up.prevent="moveResult(-1)"
          @input="results = null"
        />
        <ul v-if="results?.length" class="prow__list" role="listbox">
          <li
            v-for="(p, i) in results"
            :key="p.id"
            role="option"
            :aria-selected="i === active"
            class="prow__item"
            :class="{ 'is-active': i === active }"
            @mousedown.prevent="choose(p)"
          >
            <span class="picker__code">{{ p.productCode }}</span>
            <span>{{ p.name }}</span>
            <span class="picker__hint">
              {{ itemTypeLabels[p.itemType] }} · {{ targetLabels[p.target] }}
            </span>
            <span class="picker__price">{{ formatWon(p.listPrice) }}</span>
          </li>
        </ul>
        <div v-else-if="results" class="prow__list prow__empty">
          등록된 상품이 없습니다
          <el-button size="small" @click="mode = 'direct'">직접 입력</el-button>
        </div>
      </div>
      <div class="ft-label is-required">색상 / 사이즈</div>
      <div class="ft-value">
        <div class="ft-inline">
          <el-select
            v-model="color"
            placeholder="색상"
            :disabled="!product"
            class="prow__select"
            @change="size = null"
          >
            <el-option v-for="c in colors" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="size" placeholder="사이즈" :disabled="!color" class="prow__select">
            <el-option
              v-for="sz in sizes"
              :key="sz"
              :label="sizeLabel(sz, product.itemType, product.target)"
              :value="sz"
            />
          </el-select>
          <el-button type="primary" :disabled="!sku" @click="addRegistered">담기</el-button>
        </div>
      </div>
    </template>

    <template v-else>
      <label class="ft-label is-required" for="pp-name">상품명</label>
      <div class="ft-value">
        <el-input id="pp-name" v-model="direct.productName" placeholder="상품명" maxlength="100" />
      </div>
      <div class="ft-label is-required">품목 / 대상</div>
      <div class="ft-value">
        <div class="ft-inline">
          <el-select
            v-model="direct.itemType"
            placeholder="품목"
            class="prow__select"
            @change="direct.size = null"
          >
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
            class="prow__select"
            @change="direct.size = null"
          >
            <el-option
              v-for="(label, code) in targetLabels"
              :key="code"
              :label="label"
              :value="code"
            />
          </el-select>
        </div>
      </div>
      <label class="ft-label" for="pp-color">색상</label>
      <div class="ft-value">
        <el-input id="pp-color" v-model="direct.color" placeholder="색상 (선택)" maxlength="30" />
      </div>
      <div class="ft-label is-required">사이즈</div>
      <div class="ft-value">
        <div class="ft-inline">
          <el-select
            v-model="direct.size"
            :placeholder="direct.itemType ? '사이즈' : '품목 먼저'"
            :disabled="!direct.itemType"
            class="prow__select"
          >
            <el-option
              v-for="sz in directSizes"
              :key="sz"
              :label="sizeLabel(sz, direct.itemType, direct.target)"
              :value="sz"
            />
          </el-select>
          <el-button type="primary" :disabled="!directReady" @click="addDirect">담기</el-button>
        </div>
      </div>
    </template>
  </div>

  <div v-else class="picker">
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
.prow__search {
  position: relative;
}
.prow__chosen {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
}
.prow__list {
  position: absolute;
  z-index: 20;
  top: calc(100% - 4px);
  left: 12px;
  width: max(100% - 24px, 520px);
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  box-shadow: 0 6px 16px rgb(15 23 42 / 0.12);
}
.prow__item {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto auto;
  white-space: nowrap;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
}
.prow__item:hover,
.prow__item.is-active {
  background: var(--el-color-primary-light-9);
}
.prow__empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  color: var(--color-text-muted);
}
.prow__select {
  width: 116px;
}
.prow .ft-inline {
  flex-wrap: nowrap;
}
.ftable.prow {
  overflow: visible;
}
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

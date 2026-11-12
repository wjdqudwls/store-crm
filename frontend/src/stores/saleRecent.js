import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 직전에 고른 판매 사원, 최근 고른 상품 5개 (05 1.3, S8).
 * 화면 메모리(Pinia)에만 두고 새로고침하면 비운다. 브라우저 저장소에는 넣지 않는다 (NFR-04c).
 */
export const useSaleRecentStore = defineStore('saleRecent', () => {
  const lastStaffId = ref(null)
  const recentProducts = ref([])

  function pickStaff(id) {
    lastStaffId.value = id
  }

  function pickProduct(product) {
    recentProducts.value = [
      product,
      ...recentProducts.value.filter((p) => p.id !== product.id),
    ].slice(0, 5)
  }

  return { lastStaffId, recentProducts, pickStaff, pickProduct }
})

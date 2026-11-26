import { http } from './http'

/**
 * 상품 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/products.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/products') : null

export const getProducts = (keyword = '') =>
  fake ? fake().then((m) => m.getProducts(keyword)) : http.get('/products', { params: { keyword } })

export const getProduct = (id) =>
  fake ? fake().then((m) => m.getProduct(id)) : http.get(`/products/${id}`)

export const createProduct = (body) =>
  fake ? fake().then((m) => m.createProduct(body)) : http.post('/products', body)

export const updateProduct = (id, body) =>
  fake ? fake().then((m) => m.updateProduct(id, body)) : http.patch(`/products/${id}`, body)

/** POST /products/import — multipart의 file 하나(.xlsx, 5MB·1,000행 이하). 응답 { importedCount } (03 4.6) */
export const importProducts = (file) => {
  if (fake) return fake().then((m) => m.importProducts(file))
  const form = new FormData()
  form.append('file', file)
  return http.post('/products/import', form)
}

/** GET /products/import-template — 엑셀 폼(.xlsx) Blob. 로그인 토큰이 필요해서 링크가 아니라 요청으로 받는다 */
export const downloadImportTemplate = () =>
  fake
    ? fake().then((m) => m.downloadImportTemplate())
    : http.get('/products/import-template', { responseType: 'blob' })

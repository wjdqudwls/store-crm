/** 화면의 반응형 값(Proxy)도 복사할 수 있게 JSON으로 깊은 복사 */
export const clone = (v) => (v === undefined ? v : JSON.parse(JSON.stringify(v)))

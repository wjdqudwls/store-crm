/** 다음 6월 1일 (6월이면 올해 6월 1일 = 지금 바로, GRD-04). 설정 화면의 "적용 시작일" 안내에 쓴다 */
export function nextJuneFirst(now = new Date()) {
  const y =
    now.getMonth() === 5
      ? now.getFullYear()
      : now.getMonth() > 5
        ? now.getFullYear() + 1
        : now.getFullYear()
  return `${y}-06-01`
}

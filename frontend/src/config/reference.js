import { isFake } from '@/api/mode'

/**
 * 참고 화면: 서버를 만들지 않는 화면(가족 편집, 설정 고치기, 본사 직원 등록)이다.
 * 사용자가 흐름을 보거나 피그마로 옮기는 용도라서 가짜 데이터 데모(fake)에서만 보이고,
 * 실서버 빌드(real)에서는 메뉴·경로·버튼을 숨긴다 (sessions D2).
 */
export const showReference = isFake

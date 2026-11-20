import GradeBadge from './GradeBadge.vue'

/** 매장 단골 등급 배지 (05 1.6 K2). 6월에는 임시 등급이 점선 테두리로 보인다 */
export default {
  title: '고객/GradeBadge',
  component: GradeBadge,
  argTypes: { grade: { control: 'select', options: ['A', 'B', 'C', 'D', 'NORMAL'] } },
  args: { grade: 'B', provisional: false },
}

export const 기본 = {}
export const 임시_등급 = { args: { grade: 'C', provisional: true } }
export const 일반 = { args: { grade: 'NORMAL' } }

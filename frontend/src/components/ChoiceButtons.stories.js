import { ref } from 'vue'
import ChoiceButtons from './ChoiceButtons.vue'

/** 허용값만 버튼으로 고르기 (05 1.3). 고른 버튼을 다시 누르면 선택이 빠진다 */
export default {
  title: '공통/ChoiceButtons',
  component: ChoiceButtons,
  render: (args) => ({
    components: { ChoiceButtons },
    setup() {
      const value = ref(args.multiple ? ['OUTER'] : '90')
      return { args, value }
    },
    template: '<ChoiceButtons v-bind="args" v-model="value" />',
  }),
}

const sizes = ['85', '90', '95', '100', '105'].map((v) => ({ value: v, label: v }))
const items = [
  { value: 'OUTER', label: '아우터' },
  { value: 'TOP', label: '상의' },
  { value: 'BOTTOM', label: '하의' },
]

export const 사이즈_하나_고르기 = { args: { options: sizes } }
export const 선호_품목_여러_개 = { args: { options: items, multiple: true } }

import LoadError from './LoadError.vue'

/** 불러오기 실패 띠 (F12, 05 1.5). 스켈레톤 자리에 뜬다 */
export default {
  title: '공통/LoadError',
  component: LoadError,
  args: { busy: false },
}

export const 서버_오류 = {
  args: { error: { message: '일시적인 오류입니다. 잠시 뒤 다시 시도해 주세요.', status: 500 } },
}
export const 연결_끊김 = {
  args: {
    error: {
      message: '저장됐는지 확인하지 못했습니다. 다시 누르면 같은 요청으로 다시 보냅니다',
      status: 0,
    },
  },
}
export const 다시_시도_중 = {
  args: { error: { message: '서버에 연결하지 못했습니다.', status: 0 }, busy: true },
}

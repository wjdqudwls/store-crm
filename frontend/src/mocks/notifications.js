/** 직원 알림함 가짜 데이터 (03 4.12 알림함). 받는 직원 ID별. 이름은 모두 가상이다 (NFR-07) */
export const notifications = {
  17: [
    {
      id: 9003,
      type: 'FOLLOW_UP',
      customerId: 1187,
      refId: 510,
      message: '오세진 · 구매 2일 · 감사 인사',
      createdAt: '2026-11-10T00:00:10+09:00',
      read: false,
    },
    {
      id: 9002,
      type: 'NOTE',
      customerId: 1024,
      refId: 6950,
      message: '김하늘 · 새 메모 (박서연)',
      createdAt: '2026-11-09T17:42:00+09:00',
      read: false,
    },
    {
      id: 9001,
      type: 'FOLLOW_UP',
      customerId: 1024,
      refId: 500,
      message: '김하늘 · 구매 2주 · 착용감 확인',
      createdAt: '2026-10-29T00:00:10+09:00',
      read: true,
    },
  ],
}

/**
 * 직원별 알림 설정 (CAL-12·13). 알림 시점 기본값은 전날, 폰 푸시는 직원이 켠다.
 * timing: NONE · DAY_BEFORE · SAME_DAY · BOTH
 */
export const notifySettings = {}
export const defaultNotifySetting = () => ({ timing: 'DAY_BEFORE', push: false })

package com.crmpos.common.service;

import com.crmpos.common.domain.NotificationType;

/**
 * 직원에게 알림을 보내는 하나의 입구 (CAL-09, ADR-029). 부르는 쪽(고객 모듈)은 문구를 정해 넘기고, 이 인터페이스 뒤에서
 * 알림함에 쌓고 웹 푸시로도 보낸다. 나중에 카톡 같은 채널을 더해도 부르는 쪽은 그대로다.
 * 문구에는 고객 이름이 들어 있어 로그에 남기지 않는다 (NFR-04b).
 */
public interface StaffNotifier {

    /**
     * @param staffId    받는 직원 (개인 계정)
     * @param customerId 관련 고객 (여러 명을 묶은 알림이면 null)
     * @param refId      FOLLOW_UP이면 할 일 ID, NOTE면 메모 ID
     */
    void send(Long staffId, NotificationType type, Long customerId, Long refId, String message);

    /** 오늘 이미 같은 문구를 보냈으면 건너뛴다. 보냈으면 true. (9시 알림을 다시 돌려도 겹치지 않게) */
    boolean sendOncePerDay(Long staffId, NotificationType type, Long customerId, Long refId, String message);
}

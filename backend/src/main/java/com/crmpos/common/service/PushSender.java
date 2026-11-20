package com.crmpos.common.service;

import com.crmpos.common.domain.PushSubscription;

/**
 * 웹 푸시 한 건을 브라우저에 보낸다 (CAL-08, ADR-035). 구현은 VAPID 키가 있을 때 라이브러리로 보내고, 키가 없으면
 * 아무것도 하지 않는다. 알림을 쌓는 쪽은 이 인터페이스만 안다.
 */
public interface PushSender {

    enum Result {
        /** 보냈다. */
        SENT,
        /** 구독이 만료됐다(410·404). 구독을 지운다. */
        GONE,
        /** 그 밖의 실패. 다음에 다시 시도한다. */
        FAILED
    }

    Result send(PushSubscription subscription, String message);
}

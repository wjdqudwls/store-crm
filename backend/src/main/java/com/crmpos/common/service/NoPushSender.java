package com.crmpos.common.service;

import com.crmpos.common.domain.PushSubscription;

/** VAPID 키가 없는 환경(로컬·테스트)의 푸시 자리: 보내지 않고 실패로 돌려준다. 알림함에는 그대로 쌓인다. */
public class NoPushSender implements PushSender {

    @Override
    public Result send(PushSubscription subscription, String message) {
        return Result.FAILED;
    }
}

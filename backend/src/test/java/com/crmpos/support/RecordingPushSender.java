package com.crmpos.support;

import com.crmpos.common.domain.PushSubscription;
import com.crmpos.common.service.PushSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트용 푸시: 보낸 문구를 기록하고, 지정한 endpoint는 결과(GONE 등)를 흉내 낸다. */
public class RecordingPushSender implements PushSender {

    public final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    public final Map<String, Result> resultByEndpoint = new ConcurrentHashMap<>();

    @Override
    public Result send(PushSubscription subscription, String message) {
        Result result = resultByEndpoint.getOrDefault(subscription.getEndpoint(), Result.SENT);
        if (result == Result.SENT) {
            messages.add(message);
        }
        return result;
    }

    public void reset() {
        messages.clear();
        resultByEndpoint.clear();
    }
}

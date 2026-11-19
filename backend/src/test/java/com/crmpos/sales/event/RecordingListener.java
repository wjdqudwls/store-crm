package com.crmpos.sales.event;

import com.crmpos.common.event.OutboxMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.event.EventListener;

/** 테스트용 받는 쪽: 받은 이벤트를 기록하고, 지정한 이벤트는 일부러 실패시킨다. */
class RecordingListener {

    final List<OutboxMessage> received = Collections.synchronizedList(new ArrayList<>());
    final Set<String> failing = Collections.synchronizedSet(new HashSet<>());

    @EventListener
    void on(OutboxMessage message) {
        received.add(message);
        if (failing.contains(message.eventId())) {
            throw new IllegalStateException("받는 쪽 처리 실패");
        }
    }

    List<String> receivedIds() {
        synchronized (received) {
            return received.stream().map(OutboxMessage::eventId).toList();
        }
    }

    void reset() {
        received.clear();
        failing.clear();
    }
}

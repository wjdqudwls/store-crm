package com.crmpos.customer.event;

import com.crmpos.common.event.OutboxMessage;
import com.crmpos.customer.service.SaleEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 판매 모듈의 outbox 이벤트를 받는 쪽 (B3-1, ADR-002). 종류로 골라 JSON을 이 모듈의 record로 읽어 서비스에 넘긴다.
 * 발행기의 전달 트랜잭션 안에서 불리므로 예외를 던지면 전달이 롤백되고 재시도된다. 알 수 없는 종류는 건너뛴다.
 * payload에 메모가 있어 로그에는 이벤트 ID·종류만 남긴다 (NFR-04b).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleEventListener {

    private final ObjectMapper objectMapper;
    private final SaleEventService service;

    @EventListener
    public void on(OutboxMessage message) {
        switch (message.eventType()) {
            case "SALE_COMPLETED" -> service.handleCompleted(message.eventId(), message.eventType(),
                    objectMapper.readValue(message.payload(), SaleCompletedEvent.class));
            case "SALE_RETURNED" -> service.handleReturned(message.eventId(), message.eventType(),
                    objectMapper.readValue(message.payload(), SaleReturnedEvent.class));
            default -> log.warn("unknown outbox event type skipped eventId={} type={}", message.eventId(),
                    message.eventType());
        }
    }
}

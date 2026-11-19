package com.crmpos.common.event;

/**
 * 모듈 사이로 전달하는 outbox 이벤트의 공통 형식 (ADR-012). 보내는 쪽(판매)은 종류와 JSON을 그대로 싣고,
 * 받는 쪽(고객)이 종류로 골라 JSON을 자기 record로 읽는다. 03-api.md 6장의 계약이 이 payload다.
 * payload에 응대 메모 같은 고객 정보가 있을 수 있어 toString은 값을 보이지 않는다 (NFR-04b).
 */
public record OutboxMessage(String eventId, String eventType, Long aggregateId, String payload) {

    @Override
    public String toString() {
        return "OutboxMessage[eventId=" + eventId + ", eventType=" + eventType + ", aggregateId=" + aggregateId + "]";
    }
}

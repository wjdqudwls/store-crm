package com.crmpos.customer.event;

import java.time.OffsetDateTime;
import java.util.List;

/** SALE_RETURNED v1을 고객 모듈 쪽에서 읽는 모양 (03-api.md 6장). */
public record SaleReturnedEvent(OffsetDateTime occurredAt, Long saleId, Long returnId, String kind,
        Long customerId, List<Line> lines) {

    public record Line(Long saleLineId, int qty, long amount) {
    }
}

package com.crmpos.sales.service;

import java.util.List;

/** outbox에 남기는 SALE_RETURNED v1 (03-api.md 6장). */
public record SaleReturnedPayload(String eventId, String eventType, int version, String occurredAt, Long saleId,
        Long returnId, String kind, Long customerId, List<Line> lines) {

    public record Line(Long saleLineId, int qty, long amount) {
    }
}

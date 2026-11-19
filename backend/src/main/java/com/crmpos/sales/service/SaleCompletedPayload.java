package com.crmpos.sales.service;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** outbox에 남기는 SALE_COMPLETED v1 (03-api.md 6장). 대상·착용자를 모르면 그 칸은 빠진다. */
public record SaleCompletedPayload(String eventId, String eventType, int version, String occurredAt, Long saleId,
        String receiptNo, Long storeId, Long customerId, Long staffId, Long recordedBy, String kind,
        Long originSaleId, Long rootSaleId, Object exchange, String memo, List<Line> lines) {

    public record Line(Long saleLineId, @JsonInclude(JsonInclude.Include.NON_NULL) Target target, ItemType itemType,
            int qty, long lineAmount, @JsonInclude(JsonInclude.Include.NON_NULL) Long memberId) {
    }

    @Override
    public String toString() {
        return "SaleCompletedPayload[saleId=" + saleId + "]";
    }
}

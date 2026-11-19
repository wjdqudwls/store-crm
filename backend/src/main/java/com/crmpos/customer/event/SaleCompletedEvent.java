package com.crmpos.customer.event;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.time.OffsetDateTime;
import java.util.List;

/** SALE_COMPLETED v1을 고객 모듈 쪽에서 읽는 모양 (03-api.md 6장). 쓰지 않는 필드는 읽지 않는다. 메모가 있어 toString은 값을 가린다. */
public record SaleCompletedEvent(OffsetDateTime occurredAt, Long saleId, Long storeId, Long customerId,
        Long staffId, Long recordedBy, String kind, Long originSaleId, Long rootSaleId, String memo,
        List<Line> lines) {

    public record Line(Long saleLineId, Target target, ItemType itemType, int qty, long lineAmount) {
    }

    @Override
    public String toString() {
        return "SaleCompletedEvent[saleId=" + saleId + "]";
    }
}

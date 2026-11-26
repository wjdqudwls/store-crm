package com.crmpos.sales.service;

import com.crmpos.sales.domain.OutboxEvent;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleEventType;
import com.crmpos.sales.domain.SaleReturn;
import com.crmpos.common.web.KstTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 판매 사실을 03-api.md 6장의 SALE_COMPLETED 이벤트로 만든다. 판매 저장과 같은 트랜잭션에서 outbox에 쓴다 (ADR-002). */
@Component
@RequiredArgsConstructor
public class SaleEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent completed(Sale sale, String memo, LocalDateTime now) {
        return completed(sale, memo, null, now);
    }

    /** 교환이면 {@code exchange}에 짝 반품(영수증 사실)을 담는다 (03-api.md 6장). */
    public OutboxEvent completed(Sale sale, String memo, SaleCompletedPayload.Exchange exchange, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        List<SaleCompletedPayload.Line> lines = sale.getLines().stream()
                .map(l -> new SaleCompletedPayload.Line(l.getId(), l.getTarget(), l.getItemType(), l.getQty(),
                        l.getLineAmount(), l.getWearerMemberId()))
                .toList();
        SaleCompletedPayload payload = new SaleCompletedPayload(eventId, SaleEventType.SALE_COMPLETED.name(), 1,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(KstTime.toOffset(sale.getSoldAt())), sale.getId(),
                sale.getReceiptNo(), sale.getStoreId(), sale.getCustomerId(), sale.getStaffId(), sale.getRecordedBy(),
                sale.getKind().name(), sale.getOriginSale() == null ? null : sale.getOriginSale().getId(),
                sale.getRootSale().getId(), exchange, memo, lines);
        return OutboxEvent.pending(eventId, SaleEventType.SALE_COMPLETED, sale.getRootSale().getId(),
                objectMapper.writeValueAsString(payload), now);
    }

    /** 반품을 03-api.md 6장의 SALE_RETURNED 이벤트로 만든다. 묶음 키는 처음 판매 ID라 판매 완료 이벤트 다음에 처리된다. */
    public OutboxEvent returned(Sale sale, SaleReturn saleReturn, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        List<SaleReturnedPayload.Line> lines = saleReturn.getLines().stream()
                .map(l -> new SaleReturnedPayload.Line(l.getSaleLine().getId(), l.getQty(), l.getAmount()))
                .toList();
        SaleReturnedPayload payload = new SaleReturnedPayload(eventId, SaleEventType.SALE_RETURNED.name(), 1,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(KstTime.toOffset(saleReturn.getReturnedAt())),
                sale.getId(), saleReturn.getId(), saleReturn.getKind().name(), sale.getCustomerId(), lines);
        return OutboxEvent.pending(eventId, SaleEventType.SALE_RETURNED, sale.getRootSale().getId(),
                objectMapper.writeValueAsString(payload), now);
    }
}

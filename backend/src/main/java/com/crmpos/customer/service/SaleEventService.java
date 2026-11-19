package com.crmpos.customer.service;

import com.crmpos.common.web.KstTime;
import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerPurchase;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.NoteType;
import com.crmpos.customer.domain.ProcessedEvent;
import com.crmpos.customer.event.SaleCompletedEvent;
import com.crmpos.customer.event.SaleReturnedEvent;
import com.crmpos.customer.repository.ConsultNoteRepository;
import com.crmpos.customer.repository.CustomerPurchaseRepository;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.customer.repository.ProcessedEventRepository;
import com.crmpos.customer.repository.VisitRange;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 모듈이 보낸 이벤트를 고객 모듈 데이터로 옮긴다 (B3-1, 03-api.md 6장). 발행기의 전달 트랜잭션에 참여하므로
 * 여기서 예외가 나면 발행기가 전달을 되돌리고 다시 시도한다.
 *
 * <p>같은 이벤트가 두 번 와도 결과는 한 번이다(processed_event, NFR-03). 구매 건 = 처음 판매(rootSaleId)이고,
 * 교환·정정 라인의 실적 시각은 그 구매 건의 처음 판매 시각이다 (BR-02·17, ADR-022).
 * 등급·실적·후속 연락은 이 서비스가 하지 않는다(B3-3·B4-3). 개인정보(메모·이름)는 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SaleEventService {

    private static final String KIND_NORMAL = "NORMAL";
    private static final String KIND_CORRECTION = "CORRECTION";
    private static final String RETURN_KIND_VOID = "VOID";

    private final ProcessedEventRepository processedEventRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSummaryRepository summaryRepository;
    private final CustomerPurchaseRepository purchaseRepository;
    private final ConsultNoteRepository noteRepository;

    @Transactional
    public void handleCompleted(String eventId, String eventType, SaleCompletedEvent event) {
        if (!markProcessed(eventId, eventType)) {
            return;
        }
        if (event.customerId() == null) {
            return; // 비회원 판매는 처리 기록만 남긴다
        }
        Customer customer = findCustomer(event.customerId());
        LocalDateTime soldAt = toLocal(event.occurredAt());
        LocalDateTime countedAt = KIND_NORMAL.equals(event.kind()) ? soldAt
                : purchaseRepository.findCountedAtOfGroup(event.rootSaleId()).orElse(soldAt);

        for (SaleCompletedEvent.Line line : event.lines()) {
            if (purchaseRepository.findBySaleLineId(line.saleLineId()).isPresent()) {
                continue; // 같은 라인이 이미 있으면 다시 쌓지 않는다 (UNIQUE sale_line_id)
            }
            purchaseRepository.save(CustomerPurchase.record(customer, event.saleId(), line.saleLineId(),
                    event.storeId(), soldAt, event.rootSaleId(), countedAt, line.target(), line.itemType(),
                    line.qty(), line.lineAmount()));
        }

        if (event.memo() != null && !event.memo().isBlank()) {
            noteRepository.save(ConsultNote.purchaseMemo(customer, event.staffId(), event.recordedBy(), event.memo(),
                    event.saleId(), soldAt));
        }
        if (KIND_CORRECTION.equals(event.kind()) && event.originSaleId() != null) {
            // 원 판매의 응대 메모를 정정 판매와 (바뀐) 고객으로 옮긴다 (BR-16)
            noteRepository.findBySaleIdAndType(event.originSaleId(), NoteType.PURCHASE)
                    .forEach(note -> note.moveTo(customer, event.saleId()));
        }
        refreshVisits(customer.getId());
    }

    @Transactional
    public void handleReturned(String eventId, String eventType, SaleReturnedEvent event) {
        if (!markProcessed(eventId, eventType)) {
            return;
        }
        if (event.customerId() == null) {
            return;
        }
        boolean voided = RETURN_KIND_VOID.equals(event.kind());
        for (SaleReturnedEvent.Line line : event.lines()) {
            CustomerPurchase purchase = purchaseRepository.findBySaleLineId(line.saleLineId())
                    .orElseThrow(() -> new IllegalStateException(
                            "반품한 구매 기록이 없다 saleLineId=" + line.saleLineId()));
            purchase.applyReturn(line.qty(), line.amount(), voided);
        }
        refreshVisits(event.customerId());
    }

    /** 처음 보는 이벤트면 기록하고 true, 이미 처리했으면 false. */
    private boolean markProcessed(String eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            return false;
        }
        processedEventRepository.save(ProcessedEvent.of(eventId, eventType, now()));
        return true;
    }

    /** 첫·마지막 방문일을 구매 기록에서 다시 계산한다 (BR-09). */
    private void refreshVisits(Long customerId) {
        CustomerSummary summary = summaryRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("고객 요약이 없다 customerId=" + customerId));
        purchaseRepository.flush();
        VisitRange range = purchaseRepository.findVisitRange(customerId);
        summary.updateVisits(range.first(), range.last());
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("고객이 없다 customerId=" + customerId));
    }

    private static LocalDateTime toLocal(java.time.OffsetDateTime value) {
        return KstTime.toLocal(value).truncatedTo(ChronoUnit.MICROS);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}

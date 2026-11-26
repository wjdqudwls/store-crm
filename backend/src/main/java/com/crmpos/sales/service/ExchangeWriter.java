package com.crmpos.sales.service;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.controller.ExchangeResponse;
import com.crmpos.sales.controller.ReturnRequest;
import com.crmpos.sales.controller.SaleResponse;
import com.crmpos.sales.domain.ReceiptNumber;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleReturn;
import com.crmpos.sales.domain.SaleStatus;
import com.crmpos.sales.repository.OutboxEventRepository;
import com.crmpos.sales.repository.ReceiptSequenceRepository;
import com.crmpos.sales.repository.SaleRepository;
import com.crmpos.sales.repository.SaleReturnRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교환 저장 (SAL-06, BR-17, ADR-006·022·030): 원 판매 행을 잠그고 → 같은 키 확인 → 매장·상태·오늘 마감·남은 수량 확인 →
 * 새 영수증 번호로 새 판매를 만들고 → 돌려받는 라인을 EXCHANGE 반품으로 처리해 새 판매를 가리키게 하고 → 반품 이벤트와
 * 새 판매 이벤트를 순서대로 outbox에, 모두 한 트랜잭션이다. READ_COMMITTED (ADR-038). 반품 규칙은 {@link ReturnWriter}와 같다.
 */
@Component
@RequiredArgsConstructor
public class ExchangeWriter {

    /** 저장 결과. created=false이면 같은 키로 이미 저장된 교환이다. */
    public record Stored(ExchangeResponse response, String requestHash, boolean created) {
    }

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final OutboxEventRepository outboxRepository;
    private final SaleEventFactory eventFactory;
    private final ReturnWriter returnWriter;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Stored exchange(NewExchange e) {
        Sale original = saleRepository.findForUpdate(e.saleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));

        Optional<SaleReturn> existing = returnRepository.findByIdempotencyKey(e.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        if (!original.getStoreId().equals(e.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (original.getStatus() == SaleStatus.VOIDED) {
            throw new BusinessException(ErrorCode.SALE_ALREADY_VOIDED);
        }
        returnWriter.checkTodayNotClosed(e.storeId());
        List<SaleReturn.Item> items = resolveReturnLines(original, e);

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = now.toLocalDate();
        receiptSequenceRepository.increment(e.storeId(), today);
        String receiptNo = ReceiptNumber.format(e.storeCode(), today,
                receiptSequenceRepository.currentNo(e.storeId(), today));

        Sale replacement = Sale.exchange(original, receiptNo, e.idempotencyKey(), e.requestHash(), e.staffId(),
                e.recordedBy(), now, e.newLines());
        saleRepository.saveAndFlush(replacement);
        SaleReturn saleReturn = SaleReturn.recordExchange(original, e.idempotencyKey(), e.requestHash(), e.staffId(),
                e.recordedBy(), now, items);
        saleReturn.pairWith(replacement);
        returnRepository.saveAndFlush(saleReturn);

        // 같은 처음 판매의 이벤트는 만든 순서대로 간다: 돌려받은 것이 먼저, 새 상품이 뒤
        outboxRepository.save(eventFactory.returned(original, saleReturn, now));
        outboxRepository.save(eventFactory.completed(replacement, null,
                new SaleCompletedPayload.Exchange(saleReturn.getId(), saleReturn.getAmount()),
                now.plus(1, ChronoUnit.MICROS)));
        return new Stored(response(saleReturn, replacement), e.requestHash(), true);
    }

    /** 돌려받는 줄을 확인한다. 반품과 같은 규칙이고, 400의 칸 이름만 요청의 returnLines로 바꾼다. */
    private List<SaleReturn.Item> resolveReturnLines(Sale original, NewExchange e) {
        try {
            return returnWriter.resolveItems(original, new ReturnRequest(e.staffId(), e.returnLines()));
        } catch (ValidationFailedException ex) {
            throw new ValidationFailedException(ex.getFieldErrors().stream()
                    .map(f -> new FieldError(f.field().replaceFirst("^lines", "returnLines"), f.reason())).toList());
        }
    }

    /** 같은 키로 이미 저장된 교환이 있으면 그 응답 (동시에 같은 키가 와서 UNIQUE에 걸린 쪽이 쓴다). */
    @Transactional(readOnly = true)
    public Optional<Stored> findByKey(String key) {
        return returnRepository.findByIdempotencyKey(key).map(this::replay);
    }

    /** 같은 키를 반품·정정·기록 취소에 이미 쓴 경우는 재사용 오류다. */
    private Stored replay(SaleReturn stored) {
        if (stored.getKind() != com.crmpos.sales.domain.ReturnKind.EXCHANGE) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        Sale replacement = saleRepository.findDetailById(stored.getNewSale().getId()).orElseThrow();
        return new Stored(response(stored, replacement), stored.getRequestHash(), false);
    }

    private static ExchangeResponse response(SaleReturn saleReturn, Sale replacement) {
        return new ExchangeResponse(saleReturn.getId(), saleReturn.getAmount(), SaleResponse.from(replacement),
                replacement.getTotalAmount() - saleReturn.getAmount());
    }
}

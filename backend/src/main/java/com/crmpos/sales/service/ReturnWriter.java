package com.crmpos.sales.service;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.controller.ReturnRequest;
import com.crmpos.sales.controller.ReturnResponse;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleLine;
import com.crmpos.sales.domain.SaleReturn;
import com.crmpos.sales.domain.SaleStatus;
import com.crmpos.sales.domain.StoreDayCloseId;
import com.crmpos.sales.repository.OutboxEventRepository;
import com.crmpos.sales.repository.SaleRepository;
import com.crmpos.sales.repository.SaleReturnRepository;
import com.crmpos.sales.repository.StoreDayCloseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반품 저장: 판매 행을 잠그고 → 같은 키 확인 → 매장·상태·마감 확인 → 남은 수량 확인 → 라인·반품 행·outbox를 한 트랜잭션으로
 * 쓴다 (ADR-030). READ_COMMITTED라서 잠금을 얻은 뒤 다른 요청이 커밋한 최신 남은 수량을 읽는다 (ADR-038).
 */
@Component
@RequiredArgsConstructor
public class ReturnWriter {

    /** 저장 결과. created=false이면 같은 키로 이미 저장된 반품이다. */
    public record Stored(ReturnResponse response, String requestHash, boolean created) {
    }

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final StoreDayCloseRepository dayCloseRepository;
    private final OutboxEventRepository outboxRepository;
    private final SaleEventFactory eventFactory;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Stored record(Long saleId, Long storeId, Long recordedBy, String key, String requestHash,
            ReturnRequest request) {
        Sale sale = saleRepository.findForUpdate(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));

        // 잠금을 얻은 뒤에 다시 본다: 같은 키가 방금 다른 요청에서 저장됐을 수 있다 (재전송이면 200)
        Optional<SaleReturn> existing = returnRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new Stored(ReturnResponse.from(existing.get()), existing.get().getRequestHash(), false);
        }

        if (!sale.getStoreId().equals(storeId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new BusinessException(ErrorCode.SALE_ALREADY_VOIDED);
        }
        checkTodayNotClosed(storeId);

        List<SaleReturn.Item> items = resolveItems(sale, request);
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        SaleReturn saleReturn = SaleReturn.recordReturn(sale, key, requestHash, request.staffId(), recordedBy, now,
                items);
        returnRepository.saveAndFlush(saleReturn);
        outboxRepository.save(eventFactory.returned(sale, saleReturn, now));
        return new Stored(ReturnResponse.from(saleReturn), requestHash, true);
    }

    /** 같은 키로 이미 저장된 반품이 있으면 그 응답을 만든다 (동시에 같은 키가 와서 UNIQUE에 걸린 쪽이 쓴다). */
    @Transactional(readOnly = true)
    public Optional<Stored> findByKey(String key) {
        return returnRepository.findByIdempotencyKey(key)
                .map(r -> new Stored(ReturnResponse.from(r), r.getRequestHash(), false));
    }

    /** 반품·교환은 기록하는 오늘이 마감 전이어야 한다. 판매한 날이 지났어도 오늘이 열려 있으면 된다 (05 SCR-03). */
    void checkTodayNotClosed(Long storeId) {
        LocalDate today = LocalDate.now(KstTime.ZONE);
        dayCloseRepository.findById(new StoreDayCloseId(storeId, today))
                .filter(close -> close.getReopenedAt() == null)
                .ifPresent(close -> {
                    throw new BusinessException(ErrorCode.DAY_CLOSED);
                });
    }

    /** 요청의 줄을 이 판매의 라인으로 바꾼다. 다른 영수증의 줄은 400, 남은 수량보다 많으면 409. */
    List<SaleReturn.Item> resolveItems(Sale sale, ReturnRequest request) {
        Map<Long, SaleLine> byId = sale.getLines().stream().collect(Collectors.toMap(SaleLine::getId, Function.identity()));
        List<FieldError> errors = new ArrayList<>();
        List<SaleReturn.Item> items = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            ReturnRequest.Line requested = request.lines().get(i);
            SaleLine line = byId.get(requested.lineId());
            if (line == null) {
                errors.add(new FieldError("lines[" + i + "].lineId", "이 판매의 줄이 아닙니다."));
            } else {
                items.add(SaleReturn.Item.of(line, requested.qty()));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        for (SaleReturn.Item item : items) {
            if (item.qty() > item.line().remainingQty()) {
                throw new BusinessException(ErrorCode.RETURN_QTY_EXCEEDED,
                        Map.of("lineId", item.line().getId(), "remainingQty", item.line().remainingQty()));
            }
        }
        return items;
    }
}

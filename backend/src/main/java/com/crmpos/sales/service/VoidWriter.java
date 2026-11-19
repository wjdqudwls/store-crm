package com.crmpos.sales.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.controller.SaleDetailResponse;
import com.crmpos.sales.domain.Sale;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기록 취소 저장 (SAL-07, BR-16, ADR-026·030): 판매 행을 잠그고 → 같은 키 확인 → 잠김·상태·남은 라인 확인 → 남은 라인을
 * VOID 반품으로 돌리고 VOIDED로 바꾸고 outbox를 한 트랜잭션으로 쓴다. READ_COMMITTED (ADR-038).
 */
@Component
@RequiredArgsConstructor
public class VoidWriter {

    /** 저장 결과. created=false이면 같은 키로 이미 저장된 기록 취소다. */
    public record Stored(SaleDetailResponse response, String requestHash, boolean created) {
    }

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final StoreDayCloseRepository dayCloseRepository;
    private final OutboxEventRepository outboxRepository;
    private final SaleEventFactory eventFactory;
    private final SaleDetailBuilder detailBuilder;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Stored voidSale(Long saleId, StaffRole role, Long storeId, Long staffId, Long recordedBy, String key,
            String requestHash) {
        Sale sale = saleRepository.findForUpdate(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));

        Optional<SaleReturn> existing = returnRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Sale original = saleRepository.findDetailById(existing.get().getSale().getId()).orElseThrow();
            return new Stored(detailBuilder.build(original), existing.get().getRequestHash(), false);
        }

        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new BusinessException(ErrorCode.SALE_ALREADY_VOIDED);
        }
        if (role == StaffRole.STORE) {
            // 매장 계정은 판매한 날(오늘)이고 마감 전일 때만 취소할 수 있다. 그 밖에는 본사가 한다 (BR-16)
            if (!sale.getStoreId().equals(storeId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            LocalDate today = LocalDate.now(KstTime.ZONE);
            boolean soldToday = sale.getSoldAt().toLocalDate().equals(today);
            boolean todayClosed = dayCloseRepository.findById(new StoreDayCloseId(storeId, today))
                    .filter(close -> close.isClosed()).isPresent();
            if (!soldToday || todayClosed) {
                throw new BusinessException(ErrorCode.SALE_LOCKED);
            }
        }
        if (sale.getLines().stream().noneMatch(l -> l.remainingQty() > 0)) {
            throw new BusinessException(ErrorCode.RETURN_QTY_EXCEEDED);
        }

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        SaleReturn saleReturn = SaleReturn.recordVoid(sale, key, requestHash, staffId, recordedBy, now);
        returnRepository.saveAndFlush(saleReturn);
        outboxRepository.save(eventFactory.returned(sale, saleReturn, now));
        return new Stored(detailBuilder.build(sale), requestHash, true);
    }

    /** 같은 키로 이미 저장된 기록 취소가 있으면 그 응답을 만든다 (동시에 같은 키가 와서 UNIQUE에 걸린 쪽이 쓴다). */
    @Transactional(readOnly = true)
    public Optional<Stored> findByKey(String key) {
        return returnRepository.findByIdempotencyKey(key).map(r -> {
            Sale sale = saleRepository.findDetailById(r.getSale().getId()).orElseThrow();
            return new Stored(detailBuilder.build(sale), r.getRequestHash(), false);
        });
    }
}

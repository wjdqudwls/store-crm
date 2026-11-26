package com.crmpos.sales.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.controller.CorrectionResponse;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleReturn;
import com.crmpos.sales.domain.SaleStatus;
import com.crmpos.sales.domain.StoreDayCloseId;
import com.crmpos.sales.domain.ReceiptNumber;
import com.crmpos.sales.repository.OutboxEventRepository;
import com.crmpos.sales.repository.ReceiptSequenceRepository;
import com.crmpos.sales.repository.SaleRepository;
import com.crmpos.sales.repository.SaleReturnRepository;
import com.crmpos.sales.repository.StoreDayCloseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 정정 저장 (SAL-07, BR-16, ADR-026·030): 원 판매 행을 잠그고 → 같은 키 확인 → 잠김·상태·남은 라인 확인 →
 * 새 영수증 번호로 정정 판매를 만들고 → 남은 라인을 VOID로 돌려 VOIDED로 바꾼 행이 새 판매를 가리키게 하고 →
 * 기록 취소 이벤트와 정정 판매 이벤트를 순서대로 outbox에, 모두 한 트랜잭션이다. READ_COMMITTED (ADR-038).
 */
@Component
@RequiredArgsConstructor
public class CorrectionWriter {

    /** 저장 결과. created=false이면 같은 키로 이미 저장된 정정이다. */
    public record Stored(CorrectionResponse response, String requestHash, boolean created) {
    }

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final StoreDayCloseRepository dayCloseRepository;
    private final OutboxEventRepository outboxRepository;
    private final SaleEventFactory eventFactory;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Stored correct(NewCorrection c) {
        Sale original = saleRepository.findForUpdate(c.saleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));

        Optional<SaleReturn> existing = returnRepository.findByIdempotencyKey(c.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        if (original.getStatus() == SaleStatus.VOIDED) {
            throw new BusinessException(ErrorCode.SALE_ALREADY_VOIDED);
        }
        if (c.role() == StaffRole.STORE) {
            checkStoreMayChange(original, c.accountStoreId());
        }
        if (original.getLines().stream().noneMatch(l -> l.remainingQty() > 0)) {
            throw new BusinessException(ErrorCode.RETURN_QTY_EXCEEDED);
        }

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        // 판매 당시 등급: 고객이 그대로면 원 판매의 값, 바꿨으면 새 고객의 지금 등급 (2026-11-26 사용자 결정)
        GradeCode grade = Objects.equals(original.getCustomerId(), c.customerId()) ? original.getAppliedGrade()
                : c.currentGrade();
        LocalDate saleDate = original.getSoldAt().toLocalDate();
        receiptSequenceRepository.increment(original.getStoreId(), saleDate);
        String receiptNo = ReceiptNumber.format(c.storeCode(), saleDate,
                receiptSequenceRepository.currentNo(original.getStoreId(), saleDate));

        Sale correction = Sale.correction(original, receiptNo, c.idempotencyKey(), c.requestHash(), c.recordedBy(),
                c.customerId(), grade, now, c.lines());
        saleRepository.saveAndFlush(correction);
        SaleReturn voided = SaleReturn.recordVoid(original, c.idempotencyKey(), c.requestHash(), c.staffId(),
                c.recordedBy(), now);
        voided.pairWith(correction);
        returnRepository.saveAndFlush(voided);

        // 같은 처음 판매의 이벤트는 만든 순서대로 간다. 취소가 먼저, 정정 판매가 뒤 (같은 시각이면 순서를 못 정한다)
        outboxRepository.save(eventFactory.returned(original, voided, now));
        outboxRepository.save(eventFactory.completed(correction, null, now.plus(1, ChronoUnit.MICROS)));
        return new Stored(CorrectionResponse.from(correction), c.requestHash(), true);
    }

    /** 같은 키로 이미 저장된 정정이 있으면 그 응답 (동시에 같은 키가 와서 UNIQUE에 걸린 쪽이 쓴다). */
    @Transactional(readOnly = true)
    public Optional<Stored> findByKey(String key) {
        return returnRepository.findByIdempotencyKey(key).map(this::replay);
    }

    /** 같은 키를 반품·단순 기록 취소에 이미 쓴 경우는 정정 판매가 없으므로 재사용 오류다. */
    private Stored replay(SaleReturn stored) {
        if (stored.getNewSale() == null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        Sale correction = saleRepository.findDetailById(stored.getNewSale().getId()).orElseThrow();
        return new Stored(CorrectionResponse.from(correction), stored.getRequestHash(), false);
    }

    /** 매장 계정은 자기 매장의 판매를 판매한 날(오늘)·마감 전에만 고친다. 그 밖에는 본사가 한다 (BR-16). */
    private void checkStoreMayChange(Sale sale, Long storeId) {
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
}

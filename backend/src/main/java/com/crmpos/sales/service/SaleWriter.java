package com.crmpos.sales.service;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.ReceiptNumber;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.StoreDayClose;
import com.crmpos.sales.domain.StoreDayCloseId;
import com.crmpos.sales.repository.OutboxEventRepository;
import com.crmpos.sales.repository.ReceiptSequenceRepository;
import com.crmpos.sales.repository.SaleRepository;
import com.crmpos.sales.repository.StoreDayCloseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 저장: 마감 확인 → 영수증 번호(행 잠금) → 판매·라인 → 처음 판매 지정 → outbox, 모두 한 트랜잭션이다 (ADR-002).
 * 같은 키가 동시에 오면 UNIQUE 위반이 나고, 호출한 쪽({@link SaleRecordingService})이 저장된 판매를 읽어 응답한다.
 */
@Component
@RequiredArgsConstructor
public class SaleWriter {

    private final SaleRepository saleRepository;
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final StoreDayCloseRepository dayCloseRepository;
    private final OutboxEventRepository outboxRepository;
    private final SaleEventFactory eventFactory;

    /**
     * READ_COMMITTED로 실행한다. MariaDB 11은 기본이 스냅샷 격리(innodb_snapshot_isolation)라서, 트랜잭션 도중 다른 요청이
     * 커밋한 (매장, 날짜) 순번 행을 잠그거나 쓰면 "Record has changed since last read" 오류가 난다 (ADR-038).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Sale insert(NewSale newSale) {
        LocalDate saleDate = newSale.soldAt().toLocalDate();
        checkNotClosed(newSale.storeId(), saleDate);
        String receiptNo = ReceiptNumber.format(newSale.storeCode(), saleDate,
                nextSequence(newSale.storeId(), saleDate));

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Sale sale = Sale.record(newSale.storeId(), receiptNo, newSale.idempotencyKey(), newSale.requestHash(),
                newSale.sellerStaffId(), newSale.recordedBy(), newSale.customerId(), newSale.appliedGrade(),
                newSale.soldAt(), now, newSale.lines());
        saleRepository.saveAndFlush(sale);
        sale.assignRootToSelf();
        saleRepository.saveAndFlush(sale);
        outboxRepository.save(eventFactory.completed(sale, newSale.memo(), now));
        return sale;
    }

    /** 마감된 날(마감 행이 있고 다시 열리지 않음)에는 판매를 기록할 수 없다 (BR-16). */
    private void checkNotClosed(Long storeId, LocalDate saleDate) {
        dayCloseRepository.findById(new StoreDayCloseId(storeId, saleDate))
                .filter(close -> close.getReopenedAt() == null)
                .ifPresent(close -> {
                    throw new BusinessException(ErrorCode.DAY_CLOSED);
                });
    }

    /** (매장, 날짜) 순번을 올리면서 행을 잠근다. 같은 매장에서 동시에 판매해도 번호가 겹치지 않는다 (02-erd.md 3.3). */
    private int nextSequence(Long storeId, LocalDate saleDate) {
        receiptSequenceRepository.increment(storeId, saleDate);
        return receiptSequenceRepository.currentNo(storeId, saleDate);
    }
}

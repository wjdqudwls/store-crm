package com.crmpos.sales.service;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.StoreDayClose;
import com.crmpos.sales.domain.StoreDayCloseId;
import com.crmpos.sales.repository.StoreDayCloseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 하루 마감·다시 열기를 한 트랜잭션으로 저장한다 (SAL-08, BR-16). READ_COMMITTED, ADR-038. */
@Component
@RequiredArgsConstructor
public class DayCloseWriter {

    /** 처리 결과. created=true이면 새로 만든 행, changed=false이면 이미 그 상태라 바꾼 것이 없다. */
    public record Outcome(StoreDayClose close, boolean created, boolean changed) {
    }

    private final StoreDayCloseRepository repository;

    /**
     * 오늘 마감한다. 행이 없으면 만들고(created), 다시 연 행이면 다시 마감하고(changed), 이미 마감이면 그대로 둔다.
     * 같은 날을 동시에 처음 마감하면 한쪽이 PK 중복으로 실패하므로 호출한 쪽이 한 번 다시 부른다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Outcome close(Long storeId, LocalDate today, Long closedBy) {
        StoreDayCloseId id = new StoreDayCloseId(storeId, today);
        LocalDateTime now = now();
        StoreDayClose existing = repository.findForUpdate(id).orElse(null);
        if (existing == null) {
            return new Outcome(repository.saveAndFlush(StoreDayClose.close(storeId, today, closedBy, now)), true, true);
        }
        if (existing.isClosed()) {
            return new Outcome(existing, false, false);
        }
        existing.closeAgain(closedBy, now);
        return new Outcome(repository.saveAndFlush(existing), false, true);
    }

    /** 다시 연다. 마감 행이 없으면 404, 이미 열려 있으면 그대로 돌려준다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Outcome reopen(Long storeId, LocalDate date, Long reopenedBy) {
        StoreDayClose existing = repository.findForUpdate(new StoreDayCloseId(storeId, date))
                .orElseThrow(() -> new BusinessException(ErrorCode.DAY_CLOSE_NOT_FOUND));
        boolean wasClosed = existing.isClosed();
        existing.reopen(reopenedBy, now());
        return new Outcome(repository.saveAndFlush(existing), false, wasClosed);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}

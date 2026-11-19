package com.crmpos.sales.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.controller.DayCloseItem;
import com.crmpos.sales.domain.StoreDayClose;
import com.crmpos.sales.repository.StoreDayCloseRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 마감 (SAL-08, BR-16): 매장 계정이 오늘을 마감하고, 본사가 오늘을 다시 연다. 마감 현황은 매장 계정(자기 매장)과 본사가 본다.
 * 마감한 날에는 판매·반품·교환을 새로 기록할 수 없다(판매·반품 저장이 확인한다).
 */
@Service
@RequiredArgsConstructor
public class DayCloseService {

    /** 처음 만들었으면 created=true(201), 이미 마감돼 있거나 다시 마감했으면 false(200). */
    public record CloseResult(DayCloseItem item, boolean created) {
    }

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final DayCloseWriter writer;
    private final StoreDayCloseRepository repository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;

    /** 오늘(한국 시간) 판매를 마감한다. 이미 마감이면 그대로 200이다. */
    public CloseResult close(CurrentAccount account) {
        if (account.role() != StaffRole.STORE || account.storeId() == null) {
            throw new BusinessException(ErrorCode.STORE_REQUIRED);
        }
        LocalDate today = LocalDate.now(KstTime.ZONE);
        DayCloseWriter.Outcome outcome;
        try {
            outcome = writer.close(account.storeId(), today, account.id());
        } catch (DataIntegrityViolationException e) {
            // 같은 날을 동시에 처음 마감해 PK에 걸린 쪽: 이제 행이 있으니 한 번 다시 부른다
            outcome = writer.close(account.storeId(), today, account.id());
        }
        return new CloseResult(toItem(outcome.close()), outcome.created());
    }

    /** 오늘만 다시 열 수 있다 (본사). 지난 날짜는 정정(4.9)으로 고친다. */
    public DayCloseItem reopen(Long storeId, LocalDate date, CurrentAccount account) {
        account.requireRole(StaffRole.HQ);
        if (!date.equals(LocalDate.now(KstTime.ZONE))) {
            throw new ValidationFailedException(List.of(new FieldError("date", "오늘 마감만 다시 열 수 있습니다.")));
        }
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }
        return toItem(writer.reopen(storeId, date, account.id()).close());
    }

    /** 마감 행이 있는 날만 최신순으로. 기간을 안 주면 최근 30일이다. 매장 계정은 자기 매장만 본다. */
    @Transactional(readOnly = true)
    public List<DayCloseItem> list(Long storeId, LocalDate from, LocalDate to, CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        if (account.role() == StaffRole.STORE && !storeId.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }
        LocalDate end = to != null ? to : LocalDate.now(KstTime.ZONE);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if (start.isAfter(end) || start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new ValidationFailedException(List.of(new FieldError("from",
                    "기간은 시작일이 끝일보다 늦지 않고 366일 이내여야 합니다.")));
        }
        List<StoreDayClose> rows = repository.findByStoreAndPeriod(storeId, start, end);
        Map<Long, String> names = names(rows);
        return rows.stream().map(row -> DayCloseItem.from(row, names.get(row.getClosedBy()),
                row.getReopenedBy() == null ? null : names.get(row.getReopenedBy()))).toList();
    }

    private DayCloseItem toItem(StoreDayClose close) {
        Map<Long, String> names = names(List.of(close));
        return DayCloseItem.from(close, names.get(close.getClosedBy()),
                close.getReopenedBy() == null ? null : names.get(close.getReopenedBy()));
    }

    private Map<Long, String> names(List<StoreDayClose> rows) {
        Set<Long> ids = rows.stream().flatMap(r -> java.util.stream.Stream.of(r.getClosedBy(), r.getReopenedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (Staff staff : staffRepository.findAllById(ids)) {
            names.put(staff.getId(), staff.getName());
        }
        return names;
    }
}

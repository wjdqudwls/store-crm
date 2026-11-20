package com.crmpos.customer.controller;

import com.crmpos.common.type.GradeCode;
import com.crmpos.customer.domain.FollowUpKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 오늘 할 일 항목 (03-api.md 4.7). 고객 이름·번호(뒷자리만)가 들어 있어 toString은 가린다 (NFR-04b).
 * {@code promotional}인데 {@code marketingAllowed}가 false면 화면이 "홍보 연락 불가"를 보인다 (BR-08, BR-14).
 */
public record FollowUpTodayItem(Long id, Long customerId, String customerName, String phoneMasked, GradeCode grade,
        String stage, boolean promotional, LocalDate dueDate, long overdueDays, FollowUpKind kind, String noteContent,
        Long saleId, OffsetDateTime soldAt, boolean marketingAllowed, String assignee, Long staffId,
        String staffName) {

    @Override
    public String toString() {
        return "FollowUpTodayItem[id=" + id + "]";
    }
}

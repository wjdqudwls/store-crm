package com.crmpos.customer.controller;

import com.crmpos.customer.domain.FollowUpKind;
import com.crmpos.customer.domain.FollowUpTaskStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 고객 상세의 할 일 항목 (03-api.md 4.12 "고객 할 일 항목"). {@code assignee}는 받는 계정이 매장 계정이면 STORE,
 * 직원이면 STAFF다. VISIT·RECALL은 saleId·soldAt이 null이고 noteContent에 메모 내용이 있다. 메모 내용은 toString에 담지 않는다.
 */
public record FollowUpItem(Long id, FollowUpKind kind, String stage, boolean promotional, Long saleId,
        OffsetDateTime soldAt, String noteContent, LocalDate dueDate, FollowUpTaskStatus status, String assignee,
        Long staffId, String staffName, OffsetDateTime closedAt) {

    @Override
    public String toString() {
        return "FollowUpItem[id=" + id + "]";
    }
}

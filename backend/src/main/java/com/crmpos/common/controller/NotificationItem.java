package com.crmpos.common.controller;

import com.crmpos.common.domain.NotificationType;
import com.crmpos.common.domain.StaffNotification;
import com.crmpos.common.web.KstTime;
import java.time.OffsetDateTime;

/** 알림함 항목 (03-api.md 4.12). 문구에 고객 이름이 있어 toString은 가린다 (NFR-04b). */
public record NotificationItem(Long id, NotificationType type, Long customerId, Long refId, String message,
        OffsetDateTime createdAt, boolean read) {

    public static NotificationItem from(StaffNotification n) {
        return new NotificationItem(n.getId(), n.getType(), n.getCustomerId(), n.getRefId(), n.getMessage(),
                KstTime.toOffset(n.getCreatedAt()), n.isRead());
    }

    @Override
    public String toString() {
        return "NotificationItem[id=" + id + "]";
    }
}

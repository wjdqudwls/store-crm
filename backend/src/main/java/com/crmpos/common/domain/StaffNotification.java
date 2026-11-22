package com.crmpos.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직원 알림함의 알림 1개 (CAL-09). 문구는 알림을 만들 때 고객 모듈이 정해 넘긴다 (ADR-029). */
@Entity
@Table(name = "staff_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 직원. 같은 common 모듈이라 연관관계로 둔다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    /** customer 모듈의 ID (ADR-003). */
    @Column
    private Long customerId;

    /** FOLLOW_UP이면 follow_up_task.id, NOTE면 consult_note.id. */
    @Column(nullable = false)
    private Long refId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** null이면 안 읽음. */
    @Column
    private LocalDateTime readAt;

    public static StaffNotification create(Staff staff, NotificationType type, Long customerId, Long refId,
            String message, LocalDateTime now) {
        StaffNotification n = new StaffNotification();
        n.staff = staff;
        n.type = type;
        n.customerId = customerId;
        n.refId = refId;
        n.message = message;
        n.createdAt = now;
        return n;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 읽음 표시. 이미 읽었으면 처음 읽은 시각을 그대로 둔다. */
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    @Override
    public String toString() {
        return "StaffNotification[id=" + id + "]";
    }
}

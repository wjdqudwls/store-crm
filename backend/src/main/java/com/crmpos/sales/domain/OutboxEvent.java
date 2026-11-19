package com.crmpos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 판매·반품과 같은 트랜잭션에서 기록하는 이벤트 (ADR-002, ADR-007). */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleEventType eventType;

    /** 판매 ID (sale.id). */
    @Column(nullable = false)
    private Long aggregateId;

    /** 03-api.md 6장의 이벤트 계약을 JSON 문자열로 그대로 저장한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime publishedAt;

    /** 판매·반품과 같은 트랜잭션에서 남기는 보낼 이벤트 (발행은 B2-4). */
    public static OutboxEvent pending(String eventId, SaleEventType type, Long aggregateId, String payload,
            LocalDateTime now) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.eventType = type;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.attempts = 0;
        event.createdAt = now;
        return event;
    }

    /** 최대 시도 횟수. 이만큼 실패하면 FAILED로 두고 더 보내지 않는다 (ADR-007). */
    public static final int MAX_ATTEMPTS = 10;

    /** 받는 쪽이 처리에 성공했다. */
    public void markPublished(LocalDateTime now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /** 처리에 실패했다. 시도 횟수를 하나 올리고, 최대 횟수에 닿으면 FAILED로 둔다. */
    public void recordFailure() {
        this.attempts += 1;
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = OutboxStatus.FAILED;
        }
    }
}

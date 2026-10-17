package com.crmpos.customer.domain;

import com.crmpos.common.type.GradeCode;
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

/**
 * 고객당 1행의 조회용 요약. 등급은 3개 컬럼으로 둔다 (ADR-004).
 * 키는 고객 ID를 그대로 쓴다.
 */
@Entity
@Table(name = "customer_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerSummary {

    @Id
    private Long customerId;

    @Column(name = "ticket_count_12m", nullable = false)
    private int ticketCount12m;

    @Column(name = "qty_12m", nullable = false)
    private int qty12m;

    @Column(name = "net_amount_12m", nullable = false)
    private long netAmount12m;

    @Column(nullable = false)
    private long lifetimeAmount;

    @Column
    private LocalDateTime firstVisitAt;

    @Column
    private LocalDateTime lastVisitAt;

    @Column(nullable = false)
    private int ticketScore;

    @Column(nullable = false)
    private int amountScore;

    @Column(nullable = false)
    private int totalScore;

    /** 지금 실적 기준 등급. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCode computedGrade;

    /** 지금 적용 중인 등급. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCode currentGrade;

    /** 마지막 정기 산정에서 확정된 등급. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCode confirmedGrade;

    @Column(nullable = false)
    private int policyVersion;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;
}

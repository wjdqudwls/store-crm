package com.crmpos.customer.domain;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후속 연락 할 일. 회원 판매 이벤트를 처리할 때 활성 단계마다 1행 만든다 (ADR-016, CAL-06).
 * 단계 이름과 홍보 여부는 복사해 두어, 설정이 바뀌어도 이미 만든 할 일은 그대로다.
 */
@Entity
@Table(name = "follow_up_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowUpTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 받는 직원: 고객의 담당 직원, 없으면 판매를 기록한 직원 (BR-14). common 모듈의 ID (ADR-003). */
    @Column(nullable = false)
    private Long staffId;

    /** 기준 판매. sales 모듈의 ID (ADR-003). */
    @Column(nullable = false)
    private Long saleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private FollowUpRule rule;

    @Column(nullable = false, length = 50)
    private String ruleName;

    @Column(nullable = false)
    private boolean promotional;

    /** 판매일 + 단계 기간. */
    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpTaskStatus status;

    /** 완료할 때 남긴 연락 기록(consult_note)의 ID. */
    @Column
    private Long noteId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime closedAt;
}

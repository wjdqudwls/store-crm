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
 * 후속 연락 할 일 (ADR-016, CAL-06·10·11). 판매 이벤트를 처리할 때 활성 단계마다 1행(SALE) 만들고,
 * 메모에서 날짜를 정하면 방문 예약(VISIT)·재연락(RECALL)이 하나 생긴다.
 * 단계 이름과 홍보 여부는 복사해 두어, 설정이 바뀌어도 이미 만든 할 일은 그대로다.
 * 상태는 OPEN에서 한 번만 바뀐다(완료·건너뛰기·취소·대체). 닫힌 할 일은 다시 열지 않는다.
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

    /** 받는 계정: 고객의 담당(직원 또는 매장 계정), 없거나 비활성이면 판매 사원 (BR-14). common 모듈의 ID (ADR-003). */
    @Column(nullable = false)
    private Long staffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpKind kind;

    /** 기준 판매(SALE만). sales 모듈의 ID (ADR-003). 수동 할 일은 null. */
    @Column
    private Long saleId;

    /** 단계(SALE만). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private FollowUpRule rule;

    /** 만든 메모(VISIT·RECALL). */
    @Column
    private Long sourceNoteId;

    @Column(nullable = false, length = 50)
    private String ruleName;

    @Column(nullable = false)
    private boolean promotional;

    /** 판매일 + 단계 기간, 또는 메모에서 고른 날짜. */
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

    /** 판매로 만드는 단계별 할 일. 단계 이름·홍보 여부를 복사한다. */
    public static FollowUpTask forSale(Customer customer, Long staffId, Long saleId, FollowUpRule rule,
            LocalDate dueDate, LocalDateTime now) {
        FollowUpTask task = base(customer, staffId, FollowUpKind.SALE, dueDate, now);
        task.saleId = saleId;
        task.rule = rule;
        task.ruleName = rule.getName();
        task.promotional = rule.isPromotional();
        return task;
    }

    /** 메모에서 만드는 방문 예약(VISIT)·재연락(RECALL) 할 일. 홍보가 아니다. */
    public static FollowUpTask manual(Customer customer, Long staffId, FollowUpKind kind, Long sourceNoteId,
            LocalDate dueDate, LocalDateTime now) {
        if (kind == FollowUpKind.SALE) {
            throw new IllegalArgumentException("수동 할 일의 종류는 VISIT 또는 RECALL이다");
        }
        FollowUpTask task = base(customer, staffId, kind, dueDate, now);
        task.sourceNoteId = sourceNoteId;
        task.ruleName = kind == FollowUpKind.VISIT ? "방문 예정" : "다시 연락";
        return task;
    }

    private static FollowUpTask base(Customer customer, Long staffId, FollowUpKind kind, LocalDate dueDate,
            LocalDateTime now) {
        FollowUpTask task = new FollowUpTask();
        task.customer = customer;
        task.staffId = staffId;
        task.kind = kind;
        task.dueDate = dueDate;
        task.status = FollowUpTaskStatus.OPEN;
        task.createdAt = now;
        return task;
    }

    public boolean isOpen() {
        return status == FollowUpTaskStatus.OPEN;
    }

    /** 연락 결과를 남기고 완료한다 (CAL-02·06). */
    public void complete(Long contactNoteId, LocalDateTime now) {
        this.noteId = contactNoteId;
        close(FollowUpTaskStatus.DONE, now);
    }

    /** 방문 예약을 '방문함'으로 완료한다. 연락 기록은 남기지 않는다 (CAL-10). */
    public void markVisited(LocalDateTime now) {
        if (kind != FollowUpKind.VISIT) {
            throw new IllegalStateException("방문 예약 할 일만 방문함으로 처리할 수 있다");
        }
        close(FollowUpTaskStatus.DONE, now);
    }

    public void skip(LocalDateTime now) {
        close(FollowUpTaskStatus.SKIPPED, now);
    }

    /** 구매 건의 상품이 모두 나가서 자동으로 취소한다 (BR-14). */
    public void cancel(LocalDateTime now) {
        close(FollowUpTaskStatus.CANCELLED, now);
    }

    /** 같은 고객이 새로 사서 새 구매 기준 할 일로 대체한다 (BR-14). */
    public void replace(LocalDateTime now) {
        close(FollowUpTaskStatus.REPLACED, now);
    }

    /** 받는 직원을 바꾼다 (퇴사 이관). 열린 할 일만 넘어간다. */
    public void reassign(Long newStaffId) {
        if (!isOpen()) {
            throw new IllegalStateException("닫힌 할 일은 넘기지 않는다");
        }
        this.staffId = newStaffId;
    }

    private void close(FollowUpTaskStatus to, LocalDateTime now) {
        if (!isOpen()) {
            throw new IllegalStateException("이미 닫힌 할 일이다");
        }
        this.status = to;
        this.closedAt = now;
    }
}

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

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상담·구매·연락 메모 (MEM-01). */
@Entity
@Table(name = "consult_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 작성 직원. 매장 계정에서 쓰면 고른 직원. common 모듈의 ID (ADR-003). */
    @Column(nullable = false)
    private Long staffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CallResult callResult;

    /** 연락(CALL)일 때 채널. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContactChannel channel;

    /** 기록한 계정. */
    @Column(nullable = false)
    private Long recordedBy;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 응대 메모일 때 판매 ID (SAL-04). */
    @Column
    private Long saleId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 판매 이벤트로 받은 응대(구매) 메모 (SAL-04). 작성 직원은 판매 사원, 기록한 계정은 이벤트의 recordedBy. */
    public static ConsultNote purchaseMemo(Customer customer, Long staffId, Long recordedBy, String content,
            Long saleId, LocalDateTime at) {
        ConsultNote note = new ConsultNote();
        note.customer = customer;
        note.staffId = staffId;
        note.type = NoteType.PURCHASE;
        note.recordedBy = recordedBy;
        note.content = content;
        note.saleId = saleId;
        note.createdAt = at;
        note.updatedAt = at;
        return note;
    }

    /** 정정으로 메모를 새 판매와 (바뀐) 고객으로 옮긴다 (BR-16). 내용·작성 직원은 그대로다. */
    public void moveTo(Customer newCustomer, Long newSaleId) {
        this.customer = newCustomer;
        this.saleId = newSaleId;
    }
}

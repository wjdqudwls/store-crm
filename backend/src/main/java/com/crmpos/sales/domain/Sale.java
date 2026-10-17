package com.crmpos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import com.crmpos.common.type.GradeCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 판매 1건 = 영수증 1장 = 티켓 1건 (BR-02). */
@Entity
@Table(name = "sale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 다른 모듈(common)의 ID라 연관관계 없이 값만 든다 (ADR-003). */
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 30)
    private String receiptNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Long staffId;

    /** null이면 비회원 판매 (BR-07). */
    @Column
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GradeCode appliedGrade;

    @Column(nullable = false)
    private LocalDateTime soldAt;

    /** 실제 결제 금액 합계 (ADR-014). */
    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private int totalQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;
}

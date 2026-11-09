package com.crmpos.sales.domain;

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

    /** 판매 사원 (판매 화면에서 고름). */
    @Column(nullable = false)
    private Long staffId;

    /** 기록한 계정. 보통 매장 계정, 마감 뒤 정정이면 본사. */
    @Column(nullable = false)
    private Long recordedBy;

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
    private SaleKind kind;

    /** 교환·정정의 원 판매 (바로 앞 판매). 일반 판매는 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_sale_id")
    private Sale originSale;

    /**
     * 처음 판매 (ADR-022). 교환·정정은 원 판매의 값을 물려받는다.
     * 일반 판매는 자기 자신인데, ID는 저장해야 생기므로 저장 직후 같은 트랜잭션에서 채운다. 그래서 DB 칸은 NULL을 허용한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_sale_id")
    private Sale rootSale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    /** 기록한 시각. 판매 시각(soldAt)과 다를 수 있다. */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}

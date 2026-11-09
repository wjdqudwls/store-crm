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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 반품·교환·기록 취소 1건. 원 판매는 고치지 않는다 (BR-16, ADR-026). */
@Entity
@Table(name = "sale_return")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnKind kind;

    /** 처리한 직원 (고름). */
    @Column(nullable = false)
    private Long staffId;

    /** 기록한 계정. */
    @Column(nullable = false)
    private Long recordedBy;

    @Column(nullable = false)
    private LocalDateTime returnedAt;

    @Column(nullable = false)
    private long amount;

    /** 짝인 새 판매 (교환으로 받은 판매, 정정 판매). 일반 반품·단순 기록 취소는 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_sale_id")
    private Sale newSale;
}

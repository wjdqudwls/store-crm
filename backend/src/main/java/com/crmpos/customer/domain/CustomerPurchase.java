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

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매 이벤트로 받은 라인 단위 구매 기록 (ADR-003). */
@Entity
@Table(name = "customer_purchase")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 아래 ID들은 sales·common 모듈의 ID (ADR-003). */
    @Column(nullable = false)
    private Long saleId;

    @Column(nullable = false)
    private Long saleLineId;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private LocalDateTime soldAt;

    @Enumerated(EnumType.STRING)
    /** 모르면 null (직접 입력 판매). */
    @Column(length = 20)
    private Target target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private int returnedQty;

    @Column(nullable = false)
    private long returnedAmount;
}

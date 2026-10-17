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

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매 라인. 상품 정보는 판매 시점 값으로 복사해 둔다 (스냅샷). 금액은 직원이 확인한 실제 결제 금액이다 (ADR-014). */
@Entity
@Table(name = "sale_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    /** null이면 등록 상품이 아닌 직접 입력. */
    @Column
    private Long skuId;

    @Column(length = 20)
    private String productCode;

    @Column(nullable = false, length = 100)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Target target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    @Column(length = 30)
    private String color;

    @Column(nullable = false, length = 20)
    private String size;

    /** 등록 상품일 때만 있다. */
    @Column
    private Long listPrice;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long lineAmount;

    @Column(nullable = false)
    private int returnedQty;

    @Column
    private Long wearerMemberId;

    @Column(length = 30)
    private String wearerLabel;
}

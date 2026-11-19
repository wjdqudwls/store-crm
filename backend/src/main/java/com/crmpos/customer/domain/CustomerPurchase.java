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

    /** 구매 건 = 처음 판매 ID (이벤트의 rootSaleId, ADR-022). */
    @Column(nullable = false)
    private Long purchaseGroupId;

    /** 실적에 넣는 시각 = 처음 판매 시각. */
    @Column(nullable = false)
    private LocalDateTime countedAt;

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

    /** 나간 수량 (반품·교환·기록 취소). */
    @Column(nullable = false)
    private int returnedQty;

    /** returnedQty 중 기록 취소분 (BR-16). */
    @Column(nullable = false)
    private int voidedQty;

    @Column(nullable = false)
    private long returnedAmount;

    /** 판매 이벤트의 라인 하나를 구매 기록으로 만든다 (처음엔 나간 수량이 없다). */
    public static CustomerPurchase record(Customer customer, Long saleId, Long saleLineId, Long storeId,
            LocalDateTime soldAt, Long purchaseGroupId, LocalDateTime countedAt, Target target, ItemType itemType,
            int qty, long amount) {
        CustomerPurchase purchase = new CustomerPurchase();
        purchase.customer = customer;
        purchase.saleId = saleId;
        purchase.saleLineId = saleLineId;
        purchase.storeId = storeId;
        purchase.soldAt = soldAt;
        purchase.purchaseGroupId = purchaseGroupId;
        purchase.countedAt = countedAt;
        purchase.target = target;
        purchase.itemType = itemType;
        purchase.qty = qty;
        purchase.amount = amount;
        return purchase;
    }

    /**
     * 반품·교환·기록 취소로 나간 수량을 더한다. 남은 수량보다 많이 나갈 수는 없다.
     * 기록 취소(voided)면 같은 수량을 voidedQty에도 더해 없던 구매로 센다 (BR-16).
     */
    public void applyReturn(int returnQty, long returnAmount, boolean voided) {
        if (returnQty <= 0 || returnQty > qty - returnedQty) {
            throw new IllegalStateException("나간 수량이 남은 수량을 넘는다 saleLineId=" + saleLineId);
        }
        returnedQty += returnQty;
        returnedAmount += returnAmount;
        if (voided) {
            voidedQty += returnQty;
        }
    }

    /** 기록 취소된 수량을 뺀 수량. 0이면 없던 구매다. */
    public int effectiveQty() {
        return qty - voidedQty;
    }
}

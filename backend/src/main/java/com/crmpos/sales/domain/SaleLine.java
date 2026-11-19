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

    /** 이 줄의 sale_return_line.amount 합계. 마지막 남은 수량의 환불액 계산에 쓴다 (BR-11, ADR-030). */
    @Column(nullable = false)
    private long returnedAmount;

    @Column
    private Long wearerMemberId;

    @Column(length = 30)
    private String wearerLabel;

    /** 라인 하나. 등록 상품이면 skuId·productCode·listPrice를 채우고, 직접 입력이면 null이다. */
    public static SaleLine of(Long skuId, String productCode, String productName, Target target, ItemType itemType,
            String color, String size, Long listPrice, int qty, long lineAmount, Long wearerMemberId,
            String wearerLabel) {
        if (qty < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        if (lineAmount < 0) {
            throw new IllegalArgumentException("결제 금액은 0 이상이어야 합니다.");
        }
        SaleLine line = new SaleLine();
        line.skuId = skuId;
        line.productCode = productCode;
        line.productName = productName;
        line.target = target;
        line.itemType = itemType;
        line.color = color;
        line.size = size;
        line.listPrice = listPrice;
        line.qty = qty;
        line.lineAmount = lineAmount;
        line.wearerMemberId = wearerMemberId;
        line.wearerLabel = wearerLabel;
        return line;
    }

    void attachTo(Sale sale) {
        this.sale = sale;
    }

    /** 아직 반품하지 않은 수량. */
    public int remainingQty() {
        return qty - returnedQty;
    }

    /**
     * 이 라인을 qty개 반품하고 환불 금액을 돌려준다 (BR-11). 남은 수량 전부를 반품하면 남은 결제 금액 전부이고,
     * 아니면 결제 금액 × 반품 수량 ÷ 판매 수량의 원 미만 버림이다. 그래서 나눠 반품해도 환불 합계가 결제 금액과 같다.
     * 호출하기 전에 판매 행을 잠가야 한다 (ADR-030).
     */
    public long processReturn(int returnQty) {
        if (returnQty < 1) {
            throw new IllegalArgumentException("반품 수량은 1 이상이어야 합니다.");
        }
        if (returnQty > remainingQty()) {
            throw new IllegalArgumentException("남은 수량보다 많이 반품할 수 없습니다.");
        }
        long refund = returnQty == remainingQty()
                ? lineAmount - returnedAmount
                : lineAmount * returnQty / qty;
        this.returnedQty += returnQty;
        this.returnedAmount += refund;
        return refund;
    }
}

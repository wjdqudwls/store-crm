package com.crmpos.sales.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL)
    private List<SaleReturnLine> lines = new ArrayList<>();

    /** 반품할 라인과 수량 한 줄. */
    public record Item(SaleLine line, int qty) {
        public static Item of(SaleLine line, int qty) {
            return new Item(line, qty);
        }
    }

    /**
     * 반품을 기록한다 (SAL-03, BR-11). 라인마다 반품 수량·환불 금액을 올리고 판매 상태를 바꾼다.
     * 호출하기 전에 판매 행을 잠그고 남은 수량을 확인해야 한다 (ADR-030). 원 판매의 금액·수량은 고치지 않는다.
     */
    public static SaleReturn recordReturn(Sale sale, String idempotencyKey, String requestHash, Long staffId,
            Long recordedBy, LocalDateTime returnedAt, List<Item> items) {
        return record(ReturnKind.RETURN, sale, idempotencyKey, requestHash, staffId, recordedBy, returnedAt, items);
    }

    /**
     * 기록 취소를 기록한다 (BR-16, ADR-026): 아직 반품하지 않은 남은 라인을 모두 돌리고 판매를 VOIDED로 바꾼다.
     * 남은 라인이 없으면(전부 반품) 취소할 것이 없어 거절한다. 호출하기 전에 판매 행을 잠가야 한다.
     */
    public static SaleReturn recordVoid(Sale sale, String idempotencyKey, String requestHash, Long staffId,
            Long recordedBy, LocalDateTime returnedAt) {
        List<Item> remaining = sale.getLines().stream().filter(l -> l.remainingQty() > 0)
                .map(l -> Item.of(l, l.remainingQty())).toList();
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("취소할 남은 라인이 없습니다.");
        }
        SaleReturn ret = record(ReturnKind.VOID, sale, idempotencyKey, requestHash, staffId, recordedBy, returnedAt,
                remaining);
        sale.markVoided();
        return ret;
    }

    /** 짝인 새 판매를 잇는다 (정정: 원 판매를 기록 취소한 행이 정정 판매를 가리킨다, BR-16). */
    public void pairWith(Sale replacement) {
        this.newSale = replacement;
    }

    private static SaleReturn record(ReturnKind kind, Sale sale, String idempotencyKey, String requestHash,
            Long staffId, Long recordedBy, LocalDateTime returnedAt, List<Item> items) {
        SaleReturn ret = new SaleReturn();
        ret.sale = sale;
        ret.idempotencyKey = idempotencyKey;
        ret.requestHash = requestHash;
        ret.kind = kind;
        ret.staffId = staffId;
        ret.recordedBy = recordedBy;
        ret.returnedAt = returnedAt;
        long total = 0;
        for (Item item : items) {
            long refund = item.line().processReturn(item.qty());
            ret.lines.add(SaleReturnLine.of(ret, item.line(), item.qty(), refund));
            total += refund;
        }
        ret.amount = total;
        if (kind == ReturnKind.RETURN) {
            sale.refreshReturnStatus();
        }
        return ret;
    }
}

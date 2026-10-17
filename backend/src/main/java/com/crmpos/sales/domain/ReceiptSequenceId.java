package com.crmpos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

/** receipt_sequence의 복합 키: (매장, 판매일). */
@Embeddable
public record ReceiptSequenceId(
        @Column(name = "store_id", nullable = false) Long storeId,
        @Column(name = "sale_date", nullable = false) LocalDate saleDate) {
}

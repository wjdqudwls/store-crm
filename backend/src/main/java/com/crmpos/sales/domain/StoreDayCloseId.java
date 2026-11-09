package com.crmpos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

/** store_day_close의 복합 키: (매장, 영업일). */
@Embeddable
public record StoreDayCloseId(
        @Column(name = "store_id", nullable = false) Long storeId,
        @Column(name = "business_date", nullable = false) LocalDate businessDate) {
}

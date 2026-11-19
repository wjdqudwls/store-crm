package com.crmpos.sales.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 매장별 그날 판매 수와 마감 여부 (03-api.md 4.12). saleCount는 기록 취소를 뺀 영수증 수다. */
public record DailySummaryItem(Long storeId, LocalDate date, long saleCount, boolean closed,
        OffsetDateTime closedAt) {
}

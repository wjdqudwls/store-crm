package com.crmpos.sales.domain;

public enum SaleStatus {
    COMPLETED,
    PARTIAL_RETURNED,
    RETURNED,
    /** 기록 취소. 없던 판매로 본다 (BR-16, ADR-026). */
    VOIDED
}

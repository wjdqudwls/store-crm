package com.crmpos.sales.controller;

/**
 * 교환 응답 (03-api.md 4.8): 돌려받은 반품과 새 판매(판매 기록 응답 모양), 그리고 {@code extraPaid} = 새 상품 합계 − 반품 금액
 * (음수면 차액 환불).
 */
public record ExchangeResponse(Long returnId, long returnedAmount, SaleResponse newSale, long extraPaid) {
}

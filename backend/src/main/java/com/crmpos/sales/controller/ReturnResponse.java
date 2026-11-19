package com.crmpos.sales.controller;

import com.crmpos.sales.domain.SaleReturn;
import com.crmpos.sales.domain.SaleStatus;

/** 반품 응답 (03-api.md 4.2): 반품 ID, 환불 금액, 판매의 상태(PARTIAL_RETURNED / RETURNED). */
public record ReturnResponse(Long returnId, long returnedAmount, SaleStatus status) {

    public static ReturnResponse from(SaleReturn saleReturn) {
        return new ReturnResponse(saleReturn.getId(), saleReturn.getAmount(), saleReturn.getSale().getStatus());
    }
}

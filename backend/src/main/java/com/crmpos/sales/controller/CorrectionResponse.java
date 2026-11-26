package com.crmpos.sales.controller;

import com.crmpos.common.type.GradeCode;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleKind;
import java.time.OffsetDateTime;
import java.util.List;

/** 정정 응답: 새 판매(판매 기록 응답 모양, 03-api.md 4.1)에 원 판매 ID를 더한다 (4.9). */
public record CorrectionResponse(Long id, String receiptNo, OffsetDateTime soldAt, SaleKind kind, Long sellerStaffId,
        Long customerId, GradeCode appliedGrade, List<SaleResponse.Line> lines, long totalAmount, int totalQty,
        Long originSaleId) {

    public static CorrectionResponse from(Sale sale) {
        SaleResponse base = SaleResponse.from(sale);
        return new CorrectionResponse(base.id(), base.receiptNo(), base.soldAt(), base.kind(), base.sellerStaffId(),
                base.customerId(), base.appliedGrade(), base.lines(), base.totalAmount(), base.totalQty(),
                sale.getOriginSale().getId());
    }
}

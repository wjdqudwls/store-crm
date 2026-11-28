package com.crmpos.sales.controller;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.sales.domain.ReturnKind;
import com.crmpos.sales.domain.SaleKind;
import com.crmpos.sales.domain.SaleStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 영수증 상세 (03-api.md 4.12). 반품·교환·기록 취소는 returns에 종류(kind)와 함께 들어간다. 고객 이름이 들어 있어 toString은 가린다. */
public record SaleDetailResponse(Long id, String receiptNo, OffsetDateTime soldAt, StoreRef store, SaleKind kind,
        SaleStatus status, Long customerId, String customerName, Long sellerStaffId, String sellerName,
        GradeCode appliedGrade, List<Line> lines, List<Return> returns, Long originSaleId, String originReceiptNo,
        int totalQty, long totalAmount) {

    public record StoreRef(Long id, String name) {
    }

    public record Line(Long lineId, Long skuId, String productCode, String productName, ItemType itemType,
            Target target, String color, String size, Long listPrice, int qty, long lineAmount, int returnedQty, long returnedAmount,
            Long wearerMemberId, String wearerLabel) {
    }

    public record Return(Long returnId, ReturnKind kind, OffsetDateTime returnedAt, String staffName, long amount,
            Long newSaleId, String newReceiptNo) {
    }

    @Override
    public String toString() {
        return "SaleDetailResponse[id=" + id + "]";
    }
}

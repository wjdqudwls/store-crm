package com.crmpos.sales.controller;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleKind;
import com.crmpos.sales.domain.SaleLine;
import java.time.OffsetDateTime;
import java.util.List;

/** 판매 기록 응답 (03-api.md 4.1). 직접 입력 라인의 skuId·productCode·listPrice는 null이다. */
public record SaleResponse(Long id, String receiptNo, OffsetDateTime soldAt, SaleKind kind, Long sellerStaffId,
        Long customerId, GradeCode appliedGrade, List<Line> lines, long totalAmount, int totalQty) {

    public record Line(Long lineId, Long skuId, String productCode, String productName, ItemType itemType,
            String color, String size, Long listPrice, int qty, long lineAmount, Long wearerMemberId,
            String wearerLabel) {

        static Line from(SaleLine line) {
            return new Line(line.getId(), line.getSkuId(), line.getProductCode(), line.getProductName(),
                    line.getItemType(), emptyToNull(line.getColor()), line.getSize(), line.getListPrice(),
                    line.getQty(), line.getLineAmount(), line.getWearerMemberId(), line.getWearerLabel());
        }

        private static String emptyToNull(String value) {
            return value == null || value.isEmpty() ? null : value;
        }
    }

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(sale.getId(), sale.getReceiptNo(), KstTime.toOffset(sale.getSoldAt()), sale.getKind(),
                sale.getStaffId(), sale.getCustomerId(), sale.getAppliedGrade(),
                sale.getLines().stream().map(Line::from).toList(), sale.getTotalAmount(), sale.getTotalQty());
    }
}

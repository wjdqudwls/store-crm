package com.crmpos.sales.controller;

import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleKind;
import com.crmpos.sales.domain.SaleLine;
import com.crmpos.sales.domain.SaleStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 고객 구매이력 항목 (03-api.md 4.12): 영수증 단위, 매장 정보 포함. keyword가 있으면 그 말이 든 줄만 담는다. */
public record PurchaseItem(Long saleId, String receiptNo, OffsetDateTime soldAt, Long storeId, String storeName,
        SaleKind kind, SaleStatus status, long totalAmount, List<Line> lines) {

    public record Line(Long lineId, String productCode, String productName, String color, String size, int qty,
            long lineAmount, int returnedQty, long returnedAmount, String wearerLabel) {

        static Line from(SaleLine line) {
            return new Line(line.getId(), line.getProductCode(), line.getProductName(),
                    line.getColor() == null || line.getColor().isEmpty() ? null : line.getColor(), line.getSize(),
                    line.getQty(), line.getLineAmount(), line.getReturnedQty(), line.getReturnedAmount(),
                    line.getWearerLabel());
        }
    }

    public static PurchaseItem from(Sale sale, String storeName, List<SaleLine> lines) {
        return new PurchaseItem(sale.getId(), sale.getReceiptNo(), KstTime.toOffset(sale.getSoldAt()),
                sale.getStoreId(), storeName, sale.getKind(), sale.getStatus(), sale.getTotalAmount(),
                lines.stream().map(Line::from).toList());
    }
}

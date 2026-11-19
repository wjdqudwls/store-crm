package com.crmpos.sales.controller;

import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleKind;
import com.crmpos.sales.domain.SaleStatus;
import java.time.OffsetDateTime;

/** 판매 내역 항목 (03-api.md 4.12). 비회원이면 customerId·customerName이 null, 교환·정정은 originReceiptNo가 있다. */
public record SaleListItem(Long id, String receiptNo, OffsetDateTime soldAt, SaleKind kind, SaleStatus status,
        Long customerId, String customerName, Long sellerStaffId, String sellerName, int totalQty, long totalAmount,
        Long originSaleId, String originReceiptNo) {

    public static SaleListItem from(Sale sale, String customerName, String sellerName) {
        return new SaleListItem(sale.getId(), sale.getReceiptNo(), KstTime.toOffset(sale.getSoldAt()), sale.getKind(),
                sale.getStatus(), sale.getCustomerId(), customerName, sale.getStaffId(), sellerName,
                sale.getTotalQty(), sale.getTotalAmount(),
                sale.getOriginSale() == null ? null : sale.getOriginSale().getId(),
                sale.getOriginSale() == null ? null : sale.getOriginSale().getReceiptNo());
    }

    @Override
    public String toString() {
        return "SaleListItem[id=" + id + "]";
    }
}

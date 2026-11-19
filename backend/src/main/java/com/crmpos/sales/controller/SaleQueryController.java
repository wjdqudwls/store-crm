package com.crmpos.sales.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.PageResponse;
import com.crmpos.sales.service.SaleQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매 내역·영수증 상세·고객 구매이력·하루 판매 요약 (SAL-05, CUS-03, ADM-03, 03-api.md 3.5·4.12). */
@RestController
@RequiredArgsConstructor
public class SaleQueryController {

    private final SaleQueryService queryService;

    @GetMapping("/sales/daily-summary")
    public List<DailySummaryItem> dailySummary(CurrentAccount account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long storeId) {
        return queryService.dailySummary(date, storeId, account);
    }

    @GetMapping("/sales")
    public PageResponse<SaleListItem> list(CurrentAccount account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return queryService.list(from, to, storeId, page, size, account);
    }

    @GetMapping("/sales/{id}")
    public SaleDetailResponse detail(CurrentAccount account, @PathVariable Long id) {
        return queryService.detail(id, account);
    }

    @GetMapping("/customers/{customerId}/purchases")
    public PageResponse<PurchaseItem> purchases(CurrentAccount account, @PathVariable Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.purchases(customerId, keyword, page, size, account);
    }
}

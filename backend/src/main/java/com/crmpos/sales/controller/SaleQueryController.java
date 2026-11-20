package com.crmpos.sales.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "판매 조회")
@RestController
@RequiredArgsConstructor
public class SaleQueryController {

    private final SaleQueryService queryService;

    @Operation(summary = "하루 판매 요약", description = "매장별 그날 판매 수와 마감 여부. 매장 계정은 자기 매장, 본사는 모든 매장(storeId로 한 매장만 가능). 기록 취소된 판매는 세지 않는다.")
    @GetMapping("/sales/daily-summary")
    public List<DailySummaryItem> dailySummary(CurrentAccount account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long storeId) {
        return queryService.dailySummary(date, storeId, account);
    }

    @Operation(summary = "판매 내역", description = "기간(from·to)의 영수증 목록. 매장 계정은 자기 매장, 본사는 storeId가 필수. 목록 형식은 content·page·size·totalElements·totalPages.")
    @GetMapping("/sales")
    public PageResponse<SaleListItem> list(CurrentAccount account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return queryService.list(from, to, storeId, page, size, account);
    }

    @Operation(summary = "영수증 상세", description = "라인·반품·교환 내역을 포함한 영수증. 다른 매장 영수증도 보기만 가능하다. 없으면 SALE_NOT_FOUND 404.")
    @GetMapping("/sales/{id}")
    public SaleDetailResponse detail(CurrentAccount account, @PathVariable Long id) {
        return queryService.detail(id, account);
    }

    @Operation(summary = "고객 구매이력", description = "고객의 영수증 단위 구매이력(최신순, 기록 취소 제외). keyword로 상품명·품번을 거른다. 없는 고객은 CUSTOMER_NOT_FOUND 404.")
    @GetMapping("/customers/{customerId}/purchases")
    public PageResponse<PurchaseItem> purchases(CurrentAccount account, @PathVariable Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.purchases(customerId, keyword, page, size, account);
    }
}

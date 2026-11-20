package com.crmpos.sales.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.service.DayCloseService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 하루 마감 (SAL-08, 03-api.md 4.10): 매장 계정이 오늘을 마감, 본사가 오늘을 다시 열기, 마감 현황 조회. */
@Tag(name = "하루 마감")
@RestController
@RequiredArgsConstructor
public class DayCloseController {

    private final DayCloseService dayCloseService;

    @Operation(summary = "오늘 판매 마감", description = "매장 계정이 오늘 판매를 잠근다. 처음이면 201, 이미 마감했으면 200. 매장 계정이 아니면 STORE_REQUIRED 403.")
    @PostMapping("/day-closes")
    public ResponseEntity<DayCloseItem> close(CurrentAccount account) {
        DayCloseService.CloseResult result = dayCloseService.close(account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @Operation(summary = "마감 현황 조회", description = "매장의 날짜별 마감 기록을 본다. 본사와 그 매장의 매장 계정만(다른 매장은 ACCESS_DENIED 403). from·to는 날짜(yyyy-MM-dd).")
    @GetMapping("/stores/{storeId}/day-closes")
    public List<DayCloseItem> list(CurrentAccount account, @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dayCloseService.list(storeId, from, to, account);
    }

    @Operation(summary = "마감 다시 열기", description = "본사만 오늘 마감을 다시 연다(그 외 ACCESS_DENIED 403). 마감 기록이 없으면 DAY_CLOSE_NOT_FOUND 404.")
    @DeleteMapping("/stores/{storeId}/day-closes/{date}")
    public DayCloseItem reopen(CurrentAccount account, @PathVariable Long storeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dayCloseService.reopen(storeId, date, account);
    }
}

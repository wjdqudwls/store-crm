package com.crmpos.sales.controller;

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
@RestController
@RequiredArgsConstructor
public class DayCloseController {

    private final DayCloseService dayCloseService;

    @PostMapping("/day-closes")
    public ResponseEntity<DayCloseItem> close(CurrentAccount account) {
        DayCloseService.CloseResult result = dayCloseService.close(account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @GetMapping("/stores/{storeId}/day-closes")
    public List<DayCloseItem> list(CurrentAccount account, @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dayCloseService.list(storeId, from, to, account);
    }

    @DeleteMapping("/stores/{storeId}/day-closes/{date}")
    public DayCloseItem reopen(CurrentAccount account, @PathVariable Long storeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dayCloseService.reopen(storeId, date, account);
    }
}

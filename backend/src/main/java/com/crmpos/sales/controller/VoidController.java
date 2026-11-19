package com.crmpos.sales.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.service.VoidRecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기록 취소 (SAL-07, 03-api.md 4.9). 응답은 처음이든 다시 보냈든 200 + 취소된 판매(영수증 상세)다. */
@RestController
@RequestMapping("/sales/{saleId}/void")
@RequiredArgsConstructor
public class VoidController {

    private final VoidRecordingService voidService;

    @PostMapping
    public SaleDetailResponse voidSale(CurrentAccount account, @PathVariable Long saleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) VoidRequest request) {
        return voidService.voidSale(saleId, idempotencyKey, request, account).sale();
    }
}

package com.crmpos.sales.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.service.SaleRecordingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매 기록 (SAL-01). 매장 계정만 한다. 같은 Idempotency-Key로 다시 보내면 저장된 판매를 200으로 돌려준다 (ADR-006). */
@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleRecordingService recordingService;

    @PostMapping
    public ResponseEntity<SaleResponse> record(CurrentAccount account,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SaleRequest request) {
        SaleRecordingService.Result result = recordingService.record(idempotencyKey, request, account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.sale());
    }
}

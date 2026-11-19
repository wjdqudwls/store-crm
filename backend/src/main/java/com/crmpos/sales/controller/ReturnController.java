package com.crmpos.sales.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.service.ReturnRecordingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 반품 (SAL-03). 판매한 매장의 매장 계정만 한다. 같은 Idempotency-Key로 다시 보내면 저장된 반품을 200으로 돌려준다 (ADR-006). */
@RestController
@RequestMapping("/sales/{saleId}/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnRecordingService recordingService;

    @PostMapping
    public ResponseEntity<ReturnResponse> recordReturn(CurrentAccount account, @PathVariable Long saleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReturnRequest request) {
        ReturnRecordingService.Result result = recordingService.record(saleId, idempotencyKey, request, account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
}

package com.crmpos.sales.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "판매 기록")
@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleRecordingService recordingService;

    @Operation(summary = "판매 기록 저장", description = "매장 계정이 고객의 구매를 기록한다(본사·직원은 STORE_REQUIRED 403). Idempotency-Key 헤더가 필수이고 같은 키로 다시 보내면 200으로 저장된 판매를 돌려준다. 마감된 날은 DAY_CLOSED 409, 다른 내용에 같은 키는 IDEMPOTENCY_KEY_REUSED 409.")
    @PostMapping
    public ResponseEntity<SaleResponse> record(CurrentAccount account,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SaleRequest request) {
        SaleRecordingService.Result result = recordingService.record(idempotencyKey, request, account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.sale());
    }
}

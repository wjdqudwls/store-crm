package com.crmpos.sales.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "반품")
@RestController
@RequestMapping("/sales/{saleId}/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnRecordingService recordingService;

    @Operation(summary = "반품 기록", description = "매장 계정이 자기 매장에서 판매한 영수증의 라인별 수량을 반품한다. 검사 순서는 404 SALE_NOT_FOUND → 403 ACCESS_DENIED(다른 매장) → 입력 400. 남은 수량 초과는 RETURN_QTY_EXCEEDED 409, 마감된 날은 DAY_CLOSED 409. Idempotency-Key 필수.")
    @PostMapping
    public ResponseEntity<ReturnResponse> recordReturn(CurrentAccount account, @PathVariable Long saleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReturnRequest request) {
        ReturnRecordingService.Result result = recordingService.record(saleId, idempotencyKey, request, account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
}

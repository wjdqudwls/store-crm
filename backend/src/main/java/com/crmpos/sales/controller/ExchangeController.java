package com.crmpos.sales.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.service.ExchangeRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** 교환 (SAL-06, 03-api.md 4.8). 원 판매의 일부를 돌려받고 새 상품을 한 번에 기록한다. 매장 계정만 한다. */
@Tag(name = "교환")
@RestController
@RequestMapping("/sales/{saleId}/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeRecordingService exchangeService;

    @Operation(summary = "교환 기록", description = "반품 라인(kind=EXCHANGE)과 새 판매(kind=EXCHANGE)를 한 트랜잭션으로 만든다. 매장 계정이 자기 매장의 판매만(STORE_REQUIRED/ACCESS_DENIED 403), 오늘 마감이면 DAY_CLOSED, 남은 수량 초과는 RETURN_QTY_EXCEEDED, 취소된 판매는 SALE_ALREADY_VOIDED 409. Idempotency-Key 필수, 처음 201 / 같은 키 재전송 200.")
    @PostMapping
    public ResponseEntity<ExchangeResponse> exchange(CurrentAccount account, @PathVariable Long saleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExchangeRequest request) {
        ExchangeRecordingService.Result result = exchangeService.exchange(saleId, idempotencyKey, request, account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
}

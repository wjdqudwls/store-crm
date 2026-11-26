package com.crmpos.sales.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.service.CorrectionRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매 정정 (SAL-07, 03-api.md 4.9). 원 판매를 기록 취소하고 원 판매 시각으로 새 판매를 만든다. */
@Tag(name = "판매 정정")
@RestController
@RequestMapping("/sales/{saleId}")
@RequiredArgsConstructor
public class CorrectionController {

    private final CorrectionRecordingService correctionService;

    @Operation(summary = "판매 정정", description = "원 판매를 기록 취소하고 원 판매 시각·판매 사원으로 새 판매(CORRECTION)를 만든다. 매장 계정은 판매한 날·마감 전만(SALE_LOCKED 409), 본사는 언제나. 이미 취소는 SALE_ALREADY_VOIDED, 남은 라인 없음은 RETURN_QTY_EXCEEDED 409. Idempotency-Key 필수, 처음 201 / 같은 키 재전송 200.")
    @PutMapping
    public ResponseEntity<CorrectionResponse> correct(CurrentAccount account, @PathVariable Long saleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CorrectionRequest request) {
        CorrectionRecordingService.Result result = correctionService.correct(saleId, idempotencyKey, request, account);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.sale());
    }
}

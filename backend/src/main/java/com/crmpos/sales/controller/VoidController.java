package com.crmpos.sales.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "기록 취소")
@RestController
@RequestMapping("/sales/{saleId}/void")
@RequiredArgsConstructor
public class VoidController {

    private final VoidRecordingService voidService;

    @Operation(summary = "판매 기록 취소", description = "판매를 없던 일로 처리한다(행은 지우지 않음). 매장 계정은 판매한 날·마감 전만, 본사는 언제나 가능. 마감·지난 날은 SALE_LOCKED 409, 이미 취소는 SALE_ALREADY_VOIDED 409, 남은 라인 없음은 RETURN_QTY_EXCEEDED 409. Idempotency-Key 필수.")
    @PostMapping
    public SaleDetailResponse voidSale(CurrentAccount account, @PathVariable Long saleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) VoidRequest request) {
        return voidService.voidSale(saleId, idempotencyKey, request, account).sale();
    }
}

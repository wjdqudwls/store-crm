package com.crmpos.sales.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 판매 정정 요청 (03-api.md 4.9, SAL-07). 판매 사원은 원 판매의 값을 그대로 쓰고 응대 메모는 받지 않는다 (BR-16).
 * {@code staffId}는 처리한 직원(매장 계정만 보낸다, 본사는 계정 자체가 처리자).
 */
public record CorrectionRequest(
        Long staffId,
        Long customerId,
        @NotEmpty @Size(max = 50) List<SaleRequest.@NotNull @Valid Line> lines) {
}

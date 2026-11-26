package com.crmpos.sales.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 교환 요청 (03-api.md 4.8, SAL-06). {@code staffId}는 교환을 처리한 직원이고 새 상품의 판매 사원도 이 직원이다. */
public record ExchangeRequest(
        @NotNull Long staffId,
        @NotEmpty @Size(max = 50) List<ReturnRequest.@NotNull @Valid Line> returnLines,
        @NotEmpty @Size(max = 50) List<SaleRequest.@NotNull @Valid Line> newLines) {
}

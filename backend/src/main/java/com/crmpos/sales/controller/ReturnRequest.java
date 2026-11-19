package com.crmpos.sales.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 반품 요청 (03-api.md 4.2, SAL-03): 처리한 직원과 줄마다 반품 수량. 환불 금액은 서버가 계산한다 (BR-11). */
public record ReturnRequest(
        @NotNull Long staffId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> lines) {

    public record Line(@NotNull Long lineId, @NotNull @Min(1) @Max(9999) Integer qty) {
    }
}

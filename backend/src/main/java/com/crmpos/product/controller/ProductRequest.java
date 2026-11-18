package com.crmpos.product.controller;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 상품 등록·수정 요청 (03-api.md 3.5, PRD-01). 수정도 같은 본문으로 전체를 바꾼다. */
public record ProductRequest(
        @Size(max = 20) String productCode,
        @NotBlank @Size(max = 100) String name,
        @NotNull ItemType itemType,
        Target target,
        @NotNull @PositiveOrZero Long listPrice,
        @Size(max = 20) List<@NotBlank @Size(max = 30) String> colors,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 20) String> sizes) {
}

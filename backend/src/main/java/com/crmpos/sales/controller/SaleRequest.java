package com.crmpos.sales.controller;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 판매 기록 요청 (03-api.md 4.1, SAL-01). 판매 매장은 받지 않고 매장 계정의 매장으로 정하고, 합계는 서버가 더한다.
 * 응대 메모(고객 정보)가 들어 있어 toString은 값을 보이지 않는다 (NFR-04b).
 */
public record SaleRequest(
        @NotNull Long sellerStaffId,
        Long customerId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> lines,
        @Size(max = 1000) String memo,
        OffsetDateTime soldAt) {

    /** 등록 상품(skuId) 또는 직접 입력(productName·itemType·size) 중 하나다. 결제 금액은 직원이 확인한 값이다. */
    public record Line(
            Long skuId,
            @Size(max = 100) String productName,
            ItemType itemType,
            Target target,
            @Size(max = 20) String size,
            @Size(max = 30) String color,
            @NotNull @Min(1) @Max(9999) Integer qty,
            @NotNull @PositiveOrZero @Max(1_000_000_000_000L) Long lineAmount,
            Long memberId) {
    }

    @Override
    public String toString() {
        return "SaleRequest[lines=" + (lines == null ? 0 : lines.size()) + "]";
    }
}

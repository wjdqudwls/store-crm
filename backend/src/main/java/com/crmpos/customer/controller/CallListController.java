package com.crmpos.customer.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.domain.CallListFilter;
import com.crmpos.customer.domain.CallListScope;
import com.crmpos.customer.domain.MemberRelation;
import com.crmpos.customer.service.CallListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 콜리스트 (CAL-01, BR-09, 03-api.md 3.3). 연락 대상 고객을 마지막 방문이 오래된 순으로 보여준다. */
@Tag(name = "콜리스트")
@RestController
@RequiredArgsConstructor
public class CallListController {

    private final CallListService service;

    @Operation(summary = "콜리스트", description = "마지막 방문이 오래된 순(구매가 없으면 가입일). 조건은 모두 선택이고 안 주면 계정별 기본값: 직원은 내 담당·최소 등급 D·90일 이상 미방문, 매장 계정은 이 매장 고객 전체·홍보 연락 가능만. 기본값을 끄려면 minGrade=NORMAL, inactiveDays=0, promoOnly=false. 본사는 storeId 필수(400), 다른 매장 지정은 직원·매장 계정에 ACCESS_DENIED 403.")
    @GetMapping("/call-list")
    public PageResponse<CallListItem> list(CurrentAccount account,
            @RequestParam(required = false) CallListScope scope, @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long storeId, @RequestParam(required = false) Boolean promoOnly,
            @RequestParam(required = false) GradeCode minGrade, @RequestParam(required = false) @Min(0) Integer inactiveDays,
            @RequestParam(required = false) Target target, @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) MemberRelation memberRelation,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        CallListFilter.Request request = new CallListFilter.Request(scope, staffId, promoOnly, minGrade, inactiveDays,
                target, itemType, memberRelation);
        return service.list(account, request, storeId, page, size);
    }
}

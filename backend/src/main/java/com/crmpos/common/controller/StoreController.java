package com.crmpos.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.service.StoreQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 매장 목록과 매장의 계정 목록 (ADM-01·03, 03-api.md 3.7). */
@Tag(name = "매장·계정")
@RestController
@RequiredArgsConstructor
public class StoreController {

    private final StoreQueryService service;

    @Operation(summary = "매장 목록", description = "본사만. 매장 정보와 활성 직원 수(직급 계정만 셈). 그 외 ACCESS_DENIED 403.")
    @GetMapping("/stores")
    public List<StoreItem> stores(CurrentAccount account) {
        return service.listStores(account);
    }

    @Operation(summary = "매장의 계정 목록", description = "본사는 그 매장의 모든 계정, 매장 계정은 자기 매장의 활성 직원만(판매 사원 고르기용). 다른 매장 403, 없는 매장 STORE_NOT_FOUND 404.")
    @GetMapping("/stores/{storeId}/staff")
    public List<StaffItem> staff(CurrentAccount account, @PathVariable Long storeId) {
        return service.listStaff(storeId, account);
    }
}

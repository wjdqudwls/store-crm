package com.crmpos.common.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.service.StoreQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 매장 목록과 매장의 계정 목록 (ADM-01·03, 03-api.md 3.7). */
@RestController
@RequiredArgsConstructor
public class StoreController {

    private final StoreQueryService service;

    @GetMapping("/stores")
    public List<StoreItem> stores(CurrentAccount account) {
        return service.listStores(account);
    }

    @GetMapping("/stores/{storeId}/staff")
    public List<StaffItem> staff(CurrentAccount account, @PathVariable Long storeId) {
        return service.listStaff(storeId, account);
    }
}

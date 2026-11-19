package com.crmpos.common.controller;

import com.crmpos.common.domain.Store;

/** 매장 목록 항목 (03-api.md 3.7 GET /stores): 매장 정보와 활성 직원 수. */
public record StoreItem(Long id, String code, String name, int activeStaffCount) {

    public static StoreItem from(Store store, int activeStaffCount) {
        return new StoreItem(store.getId(), store.getCode(), store.getName(), activeStaffCount);
    }
}

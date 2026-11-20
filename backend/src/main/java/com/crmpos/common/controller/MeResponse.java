package com.crmpos.common.controller;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;

/** 내 정보 (03-api.md 3.1 GET /me): 이름, 역할·직급, 매니저 메인/임시, 소속 매장. 본사는 매장 칸이 null이다. */
public record MeResponse(Long id, String name, StaffRole role, ManagerSlot managerSlot, Long storeId,
        String storeCode, String storeName, String loginId) {

    public static MeResponse from(Staff staff) {
        var store = staff.getStore();
        return new MeResponse(staff.getId(), staff.getName(), staff.getRole(), staff.getManagerSlot(),
                store == null ? null : store.getId(), store == null ? null : store.getCode(),
                store == null ? null : store.getName(), staff.getLoginId());
    }
}

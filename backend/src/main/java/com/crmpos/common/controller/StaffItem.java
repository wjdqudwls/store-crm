package com.crmpos.common.controller;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;

/** 매장의 계정 목록 항목 (03-api.md 3.7 GET /stores/{storeId}/staff, 4.12). 비밀번호 관련 값은 담지 않는다. */
public record StaffItem(Long id, String name, StaffRole role, ManagerSlot managerSlot, String loginId,
        String staffCode, boolean active) {

    public static StaffItem from(Staff staff) {
        return new StaffItem(staff.getId(), staff.getName(), staff.getRole(), staff.getManagerSlot(),
                staff.getLoginId(), staff.getStaffCode(), staff.isActive());
    }
}

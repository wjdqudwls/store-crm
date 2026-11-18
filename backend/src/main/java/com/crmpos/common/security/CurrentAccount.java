package com.crmpos.common.security;

import com.crmpos.common.domain.StaffRole;

/** 요청한 계정. 컨트롤러 인자로 받는다. 본사 계정은 storeId가 null이다 (01-requirements.md 2장). */
public record CurrentAccount(Long id, StaffRole role, Long storeId) {
}

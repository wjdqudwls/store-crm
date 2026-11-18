package com.crmpos.common.security;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import java.util.List;

/** 요청한 계정. 컨트롤러 인자로 받는다. 본사 계정은 storeId가 null이다 (01-requirements.md 2장). */
public record CurrentAccount(Long id, StaffRole role, Long storeId) {

    /** 허용한 역할이 아니면 403 ACCESS_DENIED. 로그인(B0-3)에서 Security 규칙으로 바뀔 때까지 쓴다. */
    public void requireRole(StaffRole... allowed) {
        if (!List.of(allowed).contains(role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}

package com.crmpos.common.security;

import com.crmpos.common.domain.StaffRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** CurrentAccount 인자 주입을 확인하는 테스트 전용 컨트롤러. */
@RestController
class AccountTestController {

    record MeResponse(Long id, StaffRole role, Long storeId) {
    }

    @GetMapping("/test/me")
    MeResponse me(CurrentAccount account) {
        return new MeResponse(account.id(), account.role(), account.storeId());
    }
}

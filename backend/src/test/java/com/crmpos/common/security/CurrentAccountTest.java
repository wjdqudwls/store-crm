package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurrentAccountTest {

    @Test
    @DisplayName("허용한 역할이면 통과한다")
    void should_pass_when_roleAllowed() {
        CurrentAccount store = new CurrentAccount(1L, StaffRole.STORE, 3L);

        assertThatCode(() -> store.requireRole(StaffRole.STORE, StaffRole.HQ)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용하지 않은 역할이면 403 ACCESS_DENIED")
    void should_throw403_when_roleNotAllowed() {
        CurrentAccount staff = new CurrentAccount(5L, StaffRole.STAFF, 3L);

        assertThatThrownBy(() -> staff.requireRole(StaffRole.STORE, StaffRole.HQ))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(403);
                });
    }
}

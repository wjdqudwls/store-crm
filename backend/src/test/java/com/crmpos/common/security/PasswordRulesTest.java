package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "ABCDEFG1", "a1b2c3d4e5", "비밀번호abcd1234"})
    @DisplayName("8자 이상이고 영문과 숫자가 모두 있으면 통과한다")
    void should_accept_when_validPassword(String password) {
        assertThat(PasswordRules.isValid(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", "        ", "한글비밀번호123", ""})
    @DisplayName("7자 이하이거나 영문 또는 숫자가 없으면 거절한다 (경계: 8자 미만)")
    void should_reject_when_invalidPassword(String password) {
        assertThat(PasswordRules.isValid(password)).isFalse();
    }

    @Test
    @DisplayName("null이면 거절하고, require는 PASSWORD_POLICY_VIOLATION을 던진다")
    void should_throw_when_nullOrInvalid() {
        assertThat(PasswordRules.isValid(null)).isFalse();
        assertThatThrownBy(() -> PasswordRules.require("short1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION));
    }
}

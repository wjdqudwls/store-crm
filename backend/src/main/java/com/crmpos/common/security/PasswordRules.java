package com.crmpos.common.security;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;

/** 비밀번호 규칙: 8자 이상, 영문과 숫자를 포함한다 (AUTH-04). */
public final class PasswordRules {

    private PasswordRules() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean letter = password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean digit = password.chars().anyMatch(c -> c >= '0' && c <= '9');
        return letter && digit;
    }

    public static void require(String password) {
        if (!isValid(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }
}

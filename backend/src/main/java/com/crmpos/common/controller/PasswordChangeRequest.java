package com.crmpos.common.controller;

import jakarta.validation.constraints.NotBlank;

/** 비밀번호 바꾸기 요청. 규칙(8자 이상, 영문·숫자)은 서비스가 검사한다(PASSWORD_POLICY_VIOLATION). */
public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[]";
    }
}

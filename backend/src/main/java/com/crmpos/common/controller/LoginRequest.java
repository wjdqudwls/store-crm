package com.crmpos.common.controller;

import jakarta.validation.constraints.NotBlank;

/** 로그인 요청. 비밀번호가 들어 있어 toString은 값을 가린다 (NFR-04b). */
public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[]";
    }
}

package com.crmpos.common.controller;

import jakarta.validation.constraints.NotBlank;

/** 토큰 재발급 요청. */
public record RefreshRequest(@NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[]";
    }
}

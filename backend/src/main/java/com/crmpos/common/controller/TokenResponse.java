package com.crmpos.common.controller;

/** 로그인·재발급 응답. expiresIn은 Access 토큰이 살아 있는 초 (AUTH-01). */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, int expiresIn) {

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + "]";
    }
}

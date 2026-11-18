package com.crmpos.common.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청한 계정을 꺼내는 자리. 로그인(B0-3) 전에는 {@link DevCurrentAccountProvider},
 * B0-3부터는 JWT 구현으로 바뀐다. 계정을 못 꺼내면 401 AUTH_TOKEN_INVALID를 던진다.
 */
public interface CurrentAccountProvider {

    CurrentAccount current(HttpServletRequest request);
}

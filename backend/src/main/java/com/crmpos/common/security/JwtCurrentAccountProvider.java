package com.crmpos.common.security;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 요청한 계정을 Spring Security의 로그인 정보(JwtAuthenticationFilter가 채움)에서 꺼낸다 (B0-3, ADR-032). */
@Component
public class JwtCurrentAccountProvider implements CurrentAccountProvider {

    @Override
    public CurrentAccount current(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccount account) {
            return account;
        }
        throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
    }
}

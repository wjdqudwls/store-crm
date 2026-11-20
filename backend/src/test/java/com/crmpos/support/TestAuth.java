package com.crmpos.support;

import com.crmpos.common.security.JwtService;

/** 테스트용 로그인 도우미: 테스트 시크릿으로 계정 ID의 Access 토큰 헤더 값("Bearer …")을 만든다 (B0-3). */
public final class TestAuth {

    /** application-test.properties의 JWT_SECRET과 같다. */
    public static final String SECRET = "test-only-jwt-secret-key-at-least-32-bytes-long!!";

    private static final JwtService JWT = new JwtService(SECRET);

    private TestAuth() {
    }

    public static String bearer(long staffId) {
        return "Bearer " + JWT.issueAccess(staffId);
    }
}

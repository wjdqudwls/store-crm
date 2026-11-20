package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long-abc";
    private static final Instant T0 = Instant.parse("2026-11-20T01:00:00Z");

    private JwtService at(Instant now) {
        return new JwtService(SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("Access 토큰은 계정 ID와 발급 시각(밀리초)을 담고 다시 읽힌다")
    void should_roundTrip_when_accessToken() {
        String token = at(T0).issueAccess(17L);

        JwtService.ParsedToken parsed = at(T0.plusSeconds(60)).parseAccess(token);

        assertThat(parsed.staffId()).isEqualTo(17L);
        assertThat(parsed.issuedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Access는 30분 뒤, Refresh는 14일 뒤 만료된다")
    void should_expire_when_ttlPassed() {
        String access = at(T0).issueAccess(1L);

        assertThat(at(T0.plusSeconds(29 * 60)).parseAccess(access).staffId()).isEqualTo(1L);
        assertThatThrownBy(() -> at(T0.plusSeconds(31 * 60)).parseAccess(access))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Refresh 토큰을 Access 자리에 쓰거나 그 반대면 거절한다")
    void should_reject_when_wrongType() {
        JwtService jwt = at(T0);
        com.crmpos.common.domain.Staff staff = com.crmpos.common.domain.Staff.create("a", "h", "n",
                com.crmpos.common.domain.StaffRole.HQ, null, null, null, LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(staff, "id", 5L);
        String refresh = jwt.issueRefresh(staff);

        assertThatThrownBy(() -> jwt.parseAccess(refresh))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
        assertThatThrownBy(() -> jwt.parseRefresh(jwt.issueAccess(5L)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
        assertThat(jwt.parseRefresh(refresh).staffId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("다른 시크릿으로 서명했거나 깨진 토큰은 AUTH_TOKEN_INVALID")
    void should_reject_when_tamperedOrForeign() {
        String foreign = new JwtService("another-secret-key-at-least-32-bytes-long-xyz", Clock.fixed(T0, ZoneOffset.UTC))
                .issueAccess(1L);

        assertThatThrownBy(() -> at(T0).parseAccess(foreign))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
        assertThatThrownBy(() -> at(T0).parseAccess("not-a-jwt"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("시크릿이 32바이트보다 짧으면 앱이 시작하지 않는다")
    void should_fail_when_secretTooShort() {
        assertThatThrownBy(() -> new JwtService("short", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("로그인 기준 시각: 밀리초 단위로 이전에 발급된 토큰만 거절한다 (같은 밀리초는 통과)")
    void should_compareIssuedAtWithValidAfterInMillis() {
        JwtService.ParsedToken parsed = at(T0).parseAccess(at(T0).issueAccess(1L));
        LocalDateTime atSame = LocalDateTime.ofInstant(T0, java.time.ZoneId.of("Asia/Seoul"));

        assertThat(parsed.issuedBefore(atSame)).isFalse();
        assertThat(parsed.issuedBefore(atSame.plusNanos(500_000))).isFalse(); // 같은 밀리초 안의 마이크로초
        assertThat(parsed.issuedBefore(atSame.plusSeconds(1))).isTrue();
        assertThat(parsed.issuedBefore(atSame.minusSeconds(1))).isFalse();
    }
}

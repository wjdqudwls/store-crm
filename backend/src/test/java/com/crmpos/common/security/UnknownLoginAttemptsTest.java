package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnknownLoginAttemptsTest {

    private static final Instant T0 = Instant.parse("2026-11-20T03:00:00Z");

    @Test
    @DisplayName("4번째까지는 안 잠기고 5번째에 잠기며 10분이 지나면 풀린다")
    void should_lockAtFifthAndUnlockAfterTenMinutes() {
        UnknownLoginAttempts attempts = new UnknownLoginAttempts();
        for (int i = 0; i < 4; i++) {
            attempts.recordFailure("ghost", T0);
        }
        assertThat(attempts.isLocked("ghost", T0)).isFalse();

        attempts.recordFailure("ghost", T0);

        assertThat(attempts.isLocked("ghost", T0)).isTrue();
        assertThat(attempts.isLocked("ghost", T0.plusSeconds(599))).isTrue();
        assertThat(attempts.isLocked("ghost", T0.plusSeconds(601))).isFalse();
    }

    @Test
    @DisplayName("아이디마다 따로 센다")
    void should_countPerLoginId() {
        UnknownLoginAttempts attempts = new UnknownLoginAttempts();
        for (int i = 0; i < 5; i++) {
            attempts.recordFailure("a", T0);
        }

        assertThat(attempts.isLocked("a", T0)).isTrue();
        assertThat(attempts.isLocked("b", T0)).isFalse();
    }
}

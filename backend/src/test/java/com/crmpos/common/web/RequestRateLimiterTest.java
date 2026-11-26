package com.crmpos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요청 수 제한기 (NFR-04a): 창 안에서 한도까지만 받고, 창이 지나면 다시 받는다. */
class RequestRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final RequestRateLimiter limiter = new RequestRateLimiter(5, 60_000, now::get);

    @Test
    @DisplayName("한도(5번)까지는 받고 6번째는 막는다")
    void should_blockSixthRequest_when_sameKeyInWindow() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).as("%d번째", i + 1).isTrue();
        }

        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("키(IP)마다 따로 센다")
    void should_countSeparately_when_differentKeys() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("1.1.1.1");
        }

        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    @DisplayName("창(1분)이 지나면 다시 받는다")
    void should_allowAgain_when_windowPassed() {
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("1.1.1.1");
        }

        now.addAndGet(59_999);
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        now.addAndGet(1);
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("reset하면 기록이 지워진다")
    void should_clear_when_reset() {
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("1.1.1.1");
        }

        limiter.reset();

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }
}

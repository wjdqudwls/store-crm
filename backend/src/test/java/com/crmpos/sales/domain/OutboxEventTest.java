package com.crmpos.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** outbox 이벤트의 상태 변화 (ADR-007): PENDING → PUBLISHED, 10번 실패하면 FAILED. */
class OutboxEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 14, 0);

    private static OutboxEvent pending() {
        return OutboxEvent.pending("e1", SaleEventType.SALE_COMPLETED, 88231L, "{}", NOW);
    }

    @Test
    @DisplayName("새 이벤트는 PENDING이고 시도 0번이다")
    void should_startPending() {
        OutboxEvent event = pending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("성공하면 PUBLISHED이고 보낸 시각이 남는다")
    void should_markPublished() {
        OutboxEvent event = pending();

        event.markPublished(NOW.plusSeconds(1));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("실패하면 시도 횟수만 오르고 9번째까지는 PENDING으로 다시 시도한다")
    void should_staypending_untilTenthFailure() {
        OutboxEvent event = pending();

        for (int i = 0; i < 9; i++) {
            event.recordFailure();
        }

        assertThat(event.getAttempts()).isEqualTo(9);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("10번째 실패에서 FAILED가 된다")
    void should_fail_onTenthFailure() {
        OutboxEvent event = pending();

        for (int i = 0; i < 10; i++) {
            event.recordFailure();
        }

        assertThat(event.getAttempts()).isEqualTo(10);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getPublishedAt()).isNull();
    }
}

package com.crmpos.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 하루 마감의 상태 변화 (SAL-08, BR-16): 마감 → 다시 열기 → 다시 마감. */
class StoreDayCloseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);
    private static final LocalDateTime EVENING = DAY.atTime(21, 5);

    @Test
    @DisplayName("마감하면 그날이 잠기고 다시 열기 기록은 비어 있다")
    void should_beClosed_afterClose() {
        StoreDayClose close = StoreDayClose.close(1L, DAY, 3L, EVENING);

        assertThat(close.isClosed()).isTrue();
        assertThat(close.getId().storeId()).isEqualTo(1L);
        assertThat(close.getId().businessDate()).isEqualTo(DAY);
        assertThat(close.getClosedBy()).isEqualTo(3L);
        assertThat(close.getReopenedAt()).isNull();
    }

    @Test
    @DisplayName("다시 열면 잠기지 않고 연 계정·시각이 남는다")
    void should_beOpen_afterReopen() {
        StoreDayClose close = StoreDayClose.close(1L, DAY, 3L, EVENING);

        close.reopen(9L, EVENING.plusHours(1));

        assertThat(close.isClosed()).isFalse();
        assertThat(close.getReopenedBy()).isEqualTo(9L);
        assertThat(close.getReopenedAt()).isEqualTo(EVENING.plusHours(1));
    }

    @Test
    @DisplayName("이미 열린 날을 또 열어도 처음 연 기록이 그대로다")
    void should_keepFirstReopen_whenReopenedTwice() {
        StoreDayClose close = StoreDayClose.close(1L, DAY, 3L, EVENING);
        close.reopen(9L, EVENING.plusHours(1));

        close.reopen(10L, EVENING.plusHours(2));

        assertThat(close.getReopenedBy()).isEqualTo(9L);
        assertThat(close.getReopenedAt()).isEqualTo(EVENING.plusHours(1));
    }

    @Test
    @DisplayName("다시 마감하면 같은 행의 마감 계정·시각을 새로 쓰고 다시 열기 기록을 비운다")
    void should_resetReopenFields_whenClosedAgain() {
        StoreDayClose close = StoreDayClose.close(1L, DAY, 3L, EVENING);
        close.reopen(9L, EVENING.plusHours(1));

        close.closeAgain(4L, EVENING.plusHours(3));

        assertThat(close.isClosed()).isTrue();
        assertThat(close.getClosedBy()).isEqualTo(4L);
        assertThat(close.getClosedAt()).isEqualTo(EVENING.plusHours(3));
        assertThat(close.getReopenedBy()).isNull();
        assertThat(close.getReopenedAt()).isNull();
    }

    @Test
    @DisplayName("새로 만든 마감 행은 새 행이다 (키를 직접 정하는 엔티티라 INSERT로 저장해 동시 마감이 서로 덮어쓰지 않는다)")
    void should_beNew_whenJustCreated() {
        assertThat(StoreDayClose.close(1L, DAY, 3L, EVENING).isNew()).isTrue();
    }
}

package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시즌 구간: 6월 1일 0시에 시작해 다음 해 6월 1일 0시 전에 끝난다 (BR-03, NFR-09). */
class SeasonRangeTest {

    @Test
    @DisplayName("BR-03: 6월 1일 0시 정각은 새 시즌의 시작이다 (경계 포함)")
    void should_startNewSeason_when_juneFirstMidnight() {
        LocalDateTime at = LocalDateTime.of(2026, 6, 1, 0, 0, 0);

        assertThat(Season.startOf(at)).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(Season.endOf(at)).isEqualTo(LocalDateTime.of(2027, 6, 1, 0, 0));
    }

    @Test
    @DisplayName("BR-03: 5월 31일 23시 59분 59.999999초는 지난 시즌이다 (경계 직전)")
    void should_belongToPreviousSeason_when_justBeforeJuneFirst() {
        LocalDateTime at = LocalDateTime.of(2026, 5, 31, 23, 59, 59, 999_999_000);

        assertThat(Season.startOf(at)).isEqualTo(LocalDateTime.of(2025, 6, 1, 0, 0));
        assertThat(Season.endOf(at)).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
    }

    @Test
    @DisplayName("BR-03: 시즌 중간(11월)과 해가 바뀐 뒤(2월)도 같은 시즌의 6월 1일로 계산한다")
    void should_findSeasonStart_when_middleOfSeason() {
        assertThat(Season.startOf(LocalDateTime.of(2026, 11, 19, 10, 0))).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(Season.startOf(LocalDateTime.of(2027, 2, 3, 9, 30))).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(Season.endOf(LocalDateTime.of(2027, 2, 3, 9, 30))).isEqualTo(LocalDateTime.of(2027, 6, 1, 0, 0));
    }

    @Test
    @DisplayName("BR-03: 시즌은 12개월이고 시즌 끝은 다음 시즌의 시작과 같다 (빈틈·겹침 없음)")
    void should_chainSeasons_withoutGap() {
        LocalDateTime end = Season.endOf(LocalDateTime.of(2026, 8, 1, 12, 0));

        assertThat(Season.startOf(end)).isEqualTo(end);
    }
}

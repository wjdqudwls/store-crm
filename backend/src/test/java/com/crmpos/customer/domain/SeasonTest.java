package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시즌은 6월 1일에 시작하는 12개월이다 (BR-05, 01 BR-04 표). */
class SeasonTest {

    @Test
    @DisplayName("6월 1일부터 다음 해 5월 31일까지는 같은 시즌이다")
    void should_labelSeasonByJuneStart() {
        assertThat(Season.label(LocalDate.of(2026, 6, 1))).isEqualTo("26/27");
        assertThat(Season.label(LocalDate.of(2026, 11, 19))).isEqualTo("26/27");
        assertThat(Season.label(LocalDate.of(2027, 5, 31))).isEqualTo("26/27");
        assertThat(Season.label(LocalDate.of(2027, 6, 1))).isEqualTo("27/28");
    }

    @Test
    @DisplayName("5월 31일은 이전 시즌이다")
    void should_belongToPreviousSeason_when_beforeJune() {
        assertThat(Season.label(LocalDate.of(2026, 5, 31))).isEqualTo("25/26");
        assertThat(Season.label(LocalDate.of(2026, 1, 1))).isEqualTo("25/26");
    }

    @Test
    @DisplayName("해가 바뀌어도 두 자리 연도로 맞춘다 (2099/2100 경계)")
    void should_useTwoDigitYears() {
        assertThat(Season.label(LocalDate.of(2099, 6, 1))).isEqualTo("99/00");
        assertThat(Season.label(LocalDate.of(2005, 6, 1))).isEqualTo("05/06");
    }

    @Test
    @DisplayName("6월 한 달은 임시 기간이고 7월 1일부터 확정이다")
    void should_beProvisionalOnlyInJune() {
        assertThat(Season.isProvisional(LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(Season.isProvisional(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(Season.isProvisional(LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(Season.isProvisional(LocalDate.of(2026, 5, 31))).isFalse();
    }
}

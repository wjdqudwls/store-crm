package com.crmpos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KstTimeTest {

    @Test
    @DisplayName("한국 시간 LocalDateTime을 +09:00 OffsetDateTime으로 바꾼다")
    void should_attachKstOffset_when_localDateTime() {
        OffsetDateTime result = KstTime.toOffset(LocalDateTime.of(2026, 10, 15, 14, 3));

        assertThat(result.toString()).isEqualTo("2026-10-15T14:03+09:00");
    }

    @Test
    @DisplayName("Z(UTC)로 들어온 시각은 한국 시간으로 바꿔 LocalDateTime을 만든다")
    void should_convertToKst_when_utcInput() {
        LocalDateTime result = KstTime.toLocal(OffsetDateTime.parse("2026-10-15T05:03:00Z"));

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 15, 14, 3));
    }

    @Test
    @DisplayName("null은 null로 돌려준다")
    void should_returnNull_when_null() {
        assertThat(KstTime.toOffset(null)).isNull();
        assertThat(KstTime.toLocal(null)).isNull();
    }
}

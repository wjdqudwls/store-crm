package com.crmpos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    @DisplayName("132건을 20개씩 보면 전체 7쪽이다")
    void should_calculateTotalPages_when_132Elements() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 0, 20, 132);

        assertThat(page.totalPages()).isEqualTo(7);
        assertThat(page.totalElements()).isEqualTo(132);
        assertThat(page.content()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("빈 목록이면 전체 0쪽이다")
    void should_haveZeroPages_when_empty() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 20, 0);

        assertThat(page.totalPages()).isZero();
        assertThat(page.content()).isEmpty();
    }
}

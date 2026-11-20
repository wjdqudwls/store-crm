package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 후속 연락 순수 규칙 (BR-14, NFR-09). 날짜 경계와 받는 사람 규칙을 고정한다. */
class FollowUpPlannerTest {

    @ParameterizedTest(name = "{0} + {1}{2} = {3}")
    @CsvSource({
            "2026-10-15,2,DAY,2026-10-17",
            "2026-10-15,2,WEEK,2026-10-29",
            "2026-10-15,2,MONTH,2026-12-15",
            "2026-10-30,2,DAY,2026-11-01",
            "2026-12-30,2,DAY,2027-01-01",
            "2026-12-20,2,MONTH,2027-02-20"})
    @DisplayName("BR-14: 2일·2주는 날짜로, 2달은 달력 기준으로 더한다 (월·연말 넘김 포함)")
    void should_addCalendarOffset(LocalDate sale, int amount, FollowUpOffsetUnit unit, LocalDate expected) {
        assertThat(FollowUpPlanner.dueDate(sale, amount, unit)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} + 2달 = {1}")
    @CsvSource({"2026-12-31,2027-02-28", "2027-12-31,2028-02-29", "2026-08-31,2026-10-31", "2026-04-30,2026-06-30",
            "2026-03-31,2026-05-31"})
    @DisplayName("BR-14: 2달 뒤에 같은 날이 없으면 그 달의 마지막 날 (12/31 → 2/28, 윤년 2/29)")
    void should_clampToMonthEnd(LocalDate sale, LocalDate expected) {
        assertThat(FollowUpPlanner.dueDate(sale, 2, FollowUpOffsetUnit.MONTH)).isEqualTo(expected);
    }

    @Test
    @DisplayName("BR-14: 담당 직원이 활성이면 담당이 받는다 (매장 계정이 맡은 고객이면 그 매장 계정)")
    void should_chooseCustomerStaff_whenActive() {
        assertThat(FollowUpPlanner.chooseAssignee(17L, true, 99L)).isEqualTo(17L);
    }

    @Test
    @DisplayName("BR-14: 담당이 비활성이거나 없으면 판매 사원이 받는다")
    void should_chooseSaleStaff_whenCustomerStaffInactiveOrMissing() {
        assertThat(FollowUpPlanner.chooseAssignee(17L, false, 99L)).isEqualTo(99L);
        assertThat(FollowUpPlanner.chooseAssignee(null, false, 99L)).isEqualTo(99L);
        assertThat(FollowUpPlanner.chooseAssignee(null, true, 99L)).isEqualTo(99L);
    }

    @Test
    @DisplayName("BR-14: 마지막 방문일과 같거나 더 늦은 구매가 가장 최근 구매이고, 더 이른 구매는 아니다 (경계: 같으면 최근)")
    void should_decideLatestPurchase_withInclusiveBoundary() {
        LocalDateTime last = LocalDateTime.of(2026, 11, 19, 14, 0);

        assertThat(FollowUpPlanner.isLatestPurchase(last, last)).isTrue();
        assertThat(FollowUpPlanner.isLatestPurchase(last.plusSeconds(1), last)).isTrue();
        assertThat(FollowUpPlanner.isLatestPurchase(last.minusSeconds(1), last)).isFalse();
    }

    @Test
    @DisplayName("BR-14: 마지막 방문일이 없으면(구매가 모두 기록 취소) 최근 구매다")
    void should_beLatest_whenNoLastVisit() {
        assertThat(FollowUpPlanner.isLatestPurchase(LocalDateTime.of(2020, 1, 1, 0, 0), null)).isTrue();
    }

    @Test
    @DisplayName("BR-14: 정정이면 만기일이 오늘보다 앞선 단계는 만들지 않고, 오늘 만기는 만든다 (경계)")
    void should_skipPastStages_onlyForCorrection() {
        LocalDate today = LocalDate.of(2026, 11, 20);

        assertThat(FollowUpPlanner.shouldCreate(true, today.minusDays(1), today)).isFalse();
        assertThat(FollowUpPlanner.shouldCreate(true, today, today)).isTrue();
        assertThat(FollowUpPlanner.shouldCreate(true, today.plusDays(1), today)).isTrue();
        assertThat(FollowUpPlanner.shouldCreate(false, today.minusDays(30), today)).isTrue();
    }
}

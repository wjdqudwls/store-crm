package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 방문 예약·재연락 알림 문구 (BR-14): 한 명이면 이름을, 여러 명이면 사람 수로 묶는다. */
class ReminderMessagesTest {

    @Test
    @DisplayName("방문 예정: 1명은 이름, 여러 명은 사람 수로 묶고 전날은 '내일', 당일은 '오늘'")
    void should_formatVisit() {
        assertThat(ReminderMessages.visit(true, List.of("김하늘"))).isEqualTo("내일 김하늘님 방문 예정이에요.");
        assertThat(ReminderMessages.visit(true, List.of("김하늘", "박서연", "윤가람"))).isEqualTo("내일 고객님들이 방문할 예정이에요. (3명)");
        assertThat(ReminderMessages.visit(false, List.of("김하늘"))).isEqualTo("오늘 김하늘님 방문 예정이에요.");
        assertThat(ReminderMessages.visit(false, List.of("김하늘", "박서연"))).isEqualTo("오늘 고객님들이 방문할 예정이에요. (2명)");
    }

    @Test
    @DisplayName("다시 연락: 1명은 이름, 여러 명은 사람 수")
    void should_formatRecall() {
        assertThat(ReminderMessages.recall(false, List.of("윤가람"))).isEqualTo("오늘 윤가람님께 다시 연락할 날이에요.");
        assertThat(ReminderMessages.recall(false, List.of("윤가람", "김하늘"))).isEqualTo("오늘 다시 연락할 고객님이 2명이에요.");
        assertThat(ReminderMessages.recall(true, List.of("윤가람"))).isEqualTo("내일 윤가람님께 다시 연락할 날이에요.");
    }

    @Test
    @DisplayName("판매 후속 연락이 만기가 된 날: '고객 · 구매 2주 · 단계' (일·주·달)")
    void should_formatSaleTaskDue() {
        assertThat(ReminderMessages.saleTaskDue("김하늘", 2, FollowUpOffsetUnit.WEEK, "착용감 확인"))
                .isEqualTo("김하늘 · 구매 2주 · 착용감 확인");
        assertThat(ReminderMessages.saleTaskDue("김하늘", 2, FollowUpOffsetUnit.DAY, "감사 인사")).isEqualTo("김하늘 · 구매 2일 · 감사 인사");
        assertThat(ReminderMessages.saleTaskDue("김하늘", 2, FollowUpOffsetUnit.MONTH, "신상·재방문 안내"))
                .isEqualTo("김하늘 · 구매 2달 · 신상·재방문 안내");
    }
}

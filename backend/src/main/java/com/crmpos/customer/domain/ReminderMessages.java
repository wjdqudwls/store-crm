package com.crmpos.customer.domain;

import java.util.List;

/**
 * 방문 예약·재연락 알림 문구 (BR-14, CAL-10·11). 같은 날 같은 종류가 여럿이면 하나로 묶는다.
 * 방문 1명: "내일 김하늘님 방문 예정이에요." / 여러 명: "내일 고객님들이 방문할 예정이에요. (3명)".
 * 재연락 1명: "오늘 윤가람님께 다시 연락할 날이에요." / 여러 명: "오늘 다시 연락할 고객님이 2명이에요."
 * 전날 알림은 "내일", 당일 아침 알림은 "오늘"이다.
 */
public final class ReminderMessages {

    private ReminderMessages() {
    }

    public static String visit(boolean tomorrow, List<String> customerNames) {
        String day = tomorrow ? "내일" : "오늘";
        return customerNames.size() == 1
                ? day + " " + customerNames.get(0) + "님 방문 예정이에요."
                : day + " 고객님들이 방문할 예정이에요. (" + customerNames.size() + "명)";
    }

    public static String recall(boolean tomorrow, List<String> customerNames) {
        String day = tomorrow ? "내일" : "오늘";
        return customerNames.size() == 1
                ? day + " " + customerNames.get(0) + "님께 다시 연락할 날이에요."
                : day + " 다시 연락할 고객님이 " + customerNames.size() + "명이에요.";
    }

    /** 판매 할 일이 만기가 된 날의 문구: "김하늘 · 구매 2주 · 착용감 확인" (03-api.md 4.12 알림함 예시). */
    public static String saleTaskDue(String customerName, int amount, FollowUpOffsetUnit unit, String stage) {
        String unitText = switch (unit) {
            case DAY -> "일";
            case WEEK -> "주";
            case MONTH -> "달";
        };
        return customerName + " · 구매 " + amount + unitText + " · " + stage;
    }
}

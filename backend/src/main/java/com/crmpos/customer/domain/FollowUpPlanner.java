package com.crmpos.customer.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 후속 연락 할 일의 순수 계산 규칙 (BR-14, CAL-06). 스프링·DB를 모르는 순수 자바다.
 * 만기일 계산, 받는 사람 고르기, 가장 최근 구매 판단, 정정 때 지난 단계 거르기를 한곳에 모았다.
 */
public final class FollowUpPlanner {

    private FollowUpPlanner() {
    }

    /**
     * 만기일 = 판매일 + 단계 기간. 달력 기준이라 "2달 뒤"는 달 이름으로 더한다(10월 15일 → 12월 15일).
     * 더한 달에 같은 날이 없으면 그 달의 마지막 날로 맞춘다(12월 31일 + 2달 → 2월 28일, 윤년이면 29일).
     */
    public static LocalDate dueDate(LocalDate saleDate, int amount, FollowUpOffsetUnit unit) {
        return switch (unit) {
            case DAY -> saleDate.plusDays(amount);
            case WEEK -> saleDate.plusWeeks(amount);
            case MONTH -> saleDate.plusMonths(amount);
        };
    }

    /**
     * 받는 사람: 고객의 담당 직원이 있고 활성이면 그 담당(매장 계정이 맡은 고객이면 그 매장 계정이 담당이다),
     * 담당이 없거나 비활성이면 판매 화면에서 고른 판매 사원이다 (BR-14).
     *
     * @param customerStaffId     고객의 담당 계정 ID (없으면 null)
     * @param customerStaffActive 그 계정이 지금 활성인가 (담당이 없으면 무시)
     * @param saleStaffId         판매 사원 ID
     */
    public static Long chooseAssignee(Long customerStaffId, boolean customerStaffActive, Long saleStaffId) {
        return customerStaffId != null && customerStaffActive ? customerStaffId : saleStaffId;
    }

    /**
     * 이 구매가 고객의 가장 최근 구매인가: 구매의 실적 시각이 고객의 마지막 방문일 이상이면 그렇다.
     * 본사가 지난 날짜 판매를 정정해도 더 최근 구매의 할 일을 옛 구매의 것으로 바꾸지 않기 위해서다 (BR-14).
     * 마지막 방문일이 없으면(구매가 모두 기록 취소) 최근 구매다.
     */
    public static boolean isLatestPurchase(LocalDateTime purchaseCountedAt, LocalDateTime lastVisitAt) {
        return lastVisitAt == null || !purchaseCountedAt.isBefore(lastVisitAt);
    }

    /**
     * 정정으로 할 일을 다시 만들 때 만기일이 오늘보다 앞선 단계는 만들지 않는다(이미 연락했을 수 있다).
     * 정정이 아닌 판매는 만기일이 언제든 만든다 (BR-14).
     */
    public static boolean shouldCreate(boolean correction, LocalDate dueDate, LocalDate today) {
        return !correction || !dueDate.isBefore(today);
    }
}

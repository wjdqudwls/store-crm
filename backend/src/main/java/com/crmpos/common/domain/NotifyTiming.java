package com.crmpos.common.domain;

/** 방문 예약·재연락 알림 시점 (CAL-12): 안 받음 / 전날 / 당일 아침 / 둘 다. 기본은 전날. */
public enum NotifyTiming {
    NONE,
    DAY_BEFORE,
    SAME_DAY,
    BOTH;

    public boolean includesDayBefore() {
        return this == DAY_BEFORE || this == BOTH;
    }

    public boolean includesSameDay() {
        return this == SAME_DAY || this == BOTH;
    }
}

package com.crmpos.customer.domain;

import java.time.LocalDate;

/** 등급 시즌: 6월 1일에 시작해 다음 해 5월 31일에 끝나는 12개월 (BR-05). */
public final class Season {

    private Season() {
    }

    /** 그 날짜가 속한 시즌의 이름. 예: 2026-11-19 → "26/27" */
    public static String label(LocalDate date) {
        int startYear = date.getMonthValue() >= 6 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d/%02d", startYear % 100, (startYear + 1) % 100);
    }

    /** 6월 1일~6월 30일은 정기 산정 결과가 '임시'다. 7월 1일부터 확정이다 (BR-05). */
    public static boolean isProvisional(LocalDate date) {
        return date.getMonthValue() == 6;
    }
}

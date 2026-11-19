package com.crmpos.sales.controller;

import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.StoreDayClose;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 마감 현황 항목 (03-api.md 4.12). 마감한 계정·다시 연 계정은 이름으로 준다. */
public record DayCloseItem(LocalDate date, OffsetDateTime closedAt, String closedByName, OffsetDateTime reopenedAt,
        String reopenedByName) {

    public static DayCloseItem from(StoreDayClose close, String closedByName, String reopenedByName) {
        return new DayCloseItem(close.getId().businessDate(), KstTime.toOffset(close.getClosedAt()), closedByName,
                KstTime.toOffset(close.getReopenedAt()), reopenedByName);
    }
}

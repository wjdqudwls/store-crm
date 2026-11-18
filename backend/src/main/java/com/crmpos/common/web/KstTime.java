package com.crmpos.common.web;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 엔티티의 LocalDateTime(한국 시간)과 API의 OffsetDateTime(+09:00)을 바꾸는 한 곳 (03-api.md 1장). */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(9);

    private KstTime() {
    }

    public static OffsetDateTime toOffset(LocalDateTime local) {
        return local == null ? null : local.atOffset(OFFSET);
    }

    public static LocalDateTime toLocal(OffsetDateTime offset) {
        return offset == null ? null : offset.atZoneSameInstant(ZONE).toLocalDateTime();
    }
}

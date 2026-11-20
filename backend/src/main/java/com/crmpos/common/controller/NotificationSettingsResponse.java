package com.crmpos.common.controller;

import com.crmpos.common.domain.NotifyTiming;

/** 내 알림 설정 (CAL-08·12): 알림 시점과 이 계정에 푸시 구독이 하나라도 있는지. */
public record NotificationSettingsResponse(NotifyTiming timing, boolean push) {
}

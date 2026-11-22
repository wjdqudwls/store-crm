package com.crmpos.common.controller;

import com.crmpos.common.domain.NotifyTiming;
import jakarta.validation.constraints.NotNull;

/** 알림 시점 바꾸기 (CAL-12). */
public record NotificationSettingsRequest(@NotNull NotifyTiming timing) {
}

package com.crmpos.common.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 브라우저가 알려 준 웹 푸시 구독 (endpoint, keys.p256dh, keys.auth). 값은 로그에 남기지 않는다 (NFR-04b). */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 500) String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank @Size(max = 255) String p256dh, @NotBlank @Size(max = 255) String auth) {

        @Override
        public String toString() {
            return "Keys[]";
        }
    }

    @Override
    public String toString() {
        return "PushSubscriptionRequest[]";
    }
}

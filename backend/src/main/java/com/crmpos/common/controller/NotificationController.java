package com.crmpos.common.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 직원 알림함·알림 설정·웹 푸시 구독 (CAL-08·09·12, 03-api.md 3.7). 직원 개인 계정(MANAGER~STAFF)만 쓴다. */
@Tag(name = "알림")
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @Operation(summary = "내 알림함", description = "안 읽은 것 먼저, 그 안에서 최신순. unreadCount는 배지 숫자. 직원 개인 계정만(그 외 ACCESS_DENIED 403).")
    @GetMapping("/notifications")
    public NotificationInbox inbox(CurrentAccount account, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.inbox(account, page, size);
    }

    @Operation(summary = "알림 읽음 표시", description = "내 알림만. 없으면 NOT_FOUND 404, 남의 알림은 ACCESS_DENIED 403. 이미 읽은 알림은 그대로.")
    @PostMapping("/notifications/{id}/read")
    public NotificationItem read(CurrentAccount account, @PathVariable Long id) {
        return service.read(id, account);
    }

    @Operation(summary = "내 알림 설정 조회", description = "방문 예약·재연락 알림 시점(timing)과 이 계정의 푸시 구독 여부(push). 직원 개인 계정만.")
    @GetMapping("/me/notification-settings")
    public NotificationSettingsResponse settings(CurrentAccount account) {
        return service.settings(account);
    }

    @Operation(summary = "알림 시점 바꾸기", description = "NONE(안 받음)·DAY_BEFORE(전날, 기본)·SAME_DAY(당일 아침)·BOTH. 매일 아침 9시에 그 시점의 방문 예약·재연락을 하나로 묶어 알린다.")
    @PutMapping("/me/notification-settings")
    public NotificationSettingsResponse changeTiming(CurrentAccount account,
            @Valid @RequestBody NotificationSettingsRequest request) {
        return service.changeTiming(account, request.timing());
    }

    @Operation(summary = "웹 푸시 구독 저장", description = "이 브라우저의 구독(endpoint, keys.p256dh, keys.auth)을 저장한다. 같은 endpoint가 있으면 새 값으로 바꾼다. 성공하면 204.")
    @PostMapping("/me/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(CurrentAccount account, @Valid @RequestBody PushSubscriptionRequest request) {
        service.subscribe(account, request);
    }

    @Operation(summary = "웹 푸시 구독 지우기", description = "이 브라우저의 구독을 지운다(푸시 끄기). 없는 구독도 204.")
    @DeleteMapping("/me/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(CurrentAccount account, @RequestParam String endpoint) {
        service.unsubscribe(account, endpoint);
    }
}

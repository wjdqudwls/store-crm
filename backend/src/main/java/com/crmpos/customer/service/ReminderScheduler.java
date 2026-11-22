package com.crmpos.customer.service;

import com.crmpos.common.web.KstTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 아침 9시(한국 시간)에 방문 예약·재연락 알림을, 자정 직후 만기가 된 후속 연락 알림을 쌓는다 (CAL-09·12). 테스트는 {@code crm.reminders.enabled=false}로 끄고 직접 부른다. */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "crm.reminders.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderService reminderService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            reminderService.run(LocalDate.now(KstTime.ZONE));
        } catch (RuntimeException e) {
            // 다음 날 다시 돈다. 예외 메시지에 고객 정보가 섞일 수 있어 종류만 남긴다 (NFR-04b)
            log.error("reminder run failed type={}", e.getClass().getSimpleName());
        }
    }

    /** 자정 직후 그날 만기가 된 판매 후속 연락 할 일을 알림함에 쌓는다 (CAL-09). */
    @Scheduled(cron = "10 0 0 * * *", zone = "Asia/Seoul")
    public void notifyDueTasks() {
        try {
            reminderService.notifyDueSaleTasks(LocalDate.now(KstTime.ZONE));
        } catch (RuntimeException e) {
            log.error("due task notification failed type={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 앱이 켜질 때 놓친 알림을 채운다: 9시가 지났으면 오늘 아침 알림을, 자정 작업은 언제든 다시 돈다. 앱이 9시에 꺼져 있었던
     * 날의 알림이 빠지지 않게 한다. 알림은 (직원·종류·대상·날짜) UNIQUE라 이미 쌓인 것은 건너뛰므로 여러 번 켜져도 겹치지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStart() {
        catchUp(LocalDateTime.now(KstTime.ZONE));
    }

    void catchUp(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        try {
            reminderService.notifyDueSaleTasks(today);
            if (!now.toLocalTime().isBefore(LocalTime.of(9, 0))) {
                reminderService.run(today);
            }
        } catch (RuntimeException e) {
            log.error("reminder catch-up failed type={}", e.getClass().getSimpleName());
        }
    }
}

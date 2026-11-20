package com.crmpos.customer.service;

import com.crmpos.common.web.KstTime;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
}

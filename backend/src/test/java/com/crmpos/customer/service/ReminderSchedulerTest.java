package com.crmpos.customer.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 앱이 켜질 때 놓친 알림을 채우는 규칙 (CAL-12). 날짜별 중복은 DB UNIQUE가 막는다. */
@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    @Mock
    ReminderService reminderService;
    @InjectMocks
    ReminderScheduler scheduler;

    @Test
    @DisplayName("9시가 지나 켜지면 오늘 아침 알림과 만기 알림을 한 번씩 돌린다")
    void should_runBoth_when_startedAfterNine() {
        scheduler.catchUp(DAY.atTime(9, 30));

        verify(reminderService).run(DAY);
        verify(reminderService).notifyDueSaleTasks(DAY);
    }

    @Test
    @DisplayName("9시 정각에 켜져도 아침 알림을 돌린다")
    void should_runMorning_when_startedExactlyNine() {
        scheduler.catchUp(LocalDateTime.of(DAY, java.time.LocalTime.of(9, 0)));

        verify(reminderService).run(DAY);
    }

    @Test
    @DisplayName("9시 전에 켜지면 아침 알림은 돌리지 않고(9시에 돈다) 만기 알림만 돌린다")
    void should_runOnlyDueTasks_when_startedBeforeNine() {
        scheduler.catchUp(DAY.atTime(8, 59));

        verify(reminderService, never()).run(DAY);
        verify(reminderService).notifyDueSaleTasks(DAY);
    }

    @Test
    @DisplayName("알림 작업이 실패해도 앱 시작을 막지 않는다")
    void should_notThrow_when_reminderFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("x")).when(reminderService).notifyDueSaleTasks(DAY);

        scheduler.catchUp(DAY.atTime(10, 0)); // 예외가 새지 않는다
    }
}

package com.crmpos.customer.service;

import com.crmpos.common.domain.NotificationType;
import com.crmpos.common.domain.NotifyTiming;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.service.StaffNotifier;
import com.crmpos.customer.domain.FollowUpKind;
import com.crmpos.customer.domain.FollowUpTask;
import com.crmpos.customer.domain.FollowUpTaskStatus;
import com.crmpos.customer.domain.ReminderMessages;
import com.crmpos.customer.repository.FollowUpTaskRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문 예약·재연락 알림 (CAL-10~12, BR-14). 매일 아침 9시에 받는 직원의 알림 시점대로 알림함에 쌓고 푸시도 보낸다:
 * 전날(DAY_BEFORE)은 내일 만기, 당일(SAME_DAY)은 오늘 만기인 열린 할 일이다. 종류(방문·재연락)와 날마다 하나로 묶는다.
 * 같은 문구를 오늘 이미 보냈으면 건너뛰어 여러 번 돌려도 겹치지 않는다. 알림을 받는 쪽은 직원 개인 계정뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final Set<StaffRole> EMPLOYEE_RANKS =
            EnumSet.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);
    private static final Set<FollowUpKind> MANUAL = EnumSet.of(FollowUpKind.VISIT, FollowUpKind.RECALL);

    private final FollowUpTaskRepository taskRepository;
    private final StaffRepository staffRepository;
    private final StaffNotifier notifier;

    /** today 아침 알림을 쌓는다. 보낸 알림 수를 돌려준다. */
    @Transactional
    public int run(LocalDate today) {
        int sent = sendFor(today.plusDays(1), true, today) + sendFor(today, false, today);
        log.info("reminders sent count={}", sent);
        return sent;
    }

    /**
     * date에 만기인 할 일을 받는 직원별·종류별로 묶어 알린다.
     * tomorrow=true면 "내일" 문구(전날 알림 대상 직원), false면 "오늘" 문구(당일 알림 대상 직원)다.
     */
    private int sendFor(LocalDate date, boolean tomorrow, LocalDate today) {
        List<FollowUpTask> tasks = taskRepository.findDueOn(MANUAL, FollowUpTaskStatus.OPEN, date);
        // 직원 → 종류 → 그 할 일들 (등록 순서 유지)
        Map<Long, Map<FollowUpKind, List<FollowUpTask>>> grouped = new LinkedHashMap<>();
        for (FollowUpTask task : tasks) {
            grouped.computeIfAbsent(task.getStaffId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(task.getKind(), k -> new java.util.ArrayList<>()).add(task);
        }
        int sent = 0;
        for (Map.Entry<Long, Map<FollowUpKind, List<FollowUpTask>>> byStaff : grouped.entrySet()) {
            Staff staff = staffRepository.findById(byStaff.getKey()).orElse(null);
            if (staff == null || !staff.isActive() || !EMPLOYEE_RANKS.contains(staff.getRole())
                    || !wants(staff.getNotifyTiming(), tomorrow)) {
                continue;
            }
            for (Map.Entry<FollowUpKind, List<FollowUpTask>> byKind : byStaff.getValue().entrySet()) {
                List<FollowUpTask> group = byKind.getValue();
                List<String> names = group.stream().map(t -> t.getCustomer().getName()).toList();
                String message = byKind.getKey() == FollowUpKind.VISIT ? ReminderMessages.visit(tomorrow, names)
                        : ReminderMessages.recall(tomorrow, names);
                Long customerId = group.size() == 1 ? group.get(0).getCustomer().getId() : null;
                if (notifier.sendOncePerDay(staff.getId(), NotificationType.FOLLOW_UP, customerId, group.get(0).getId(),
                        message)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /**
     * 오늘 만기가 된 판매 후속 연락 할 일을 받는 직원 알림함에 쌓는다 (CAL-09 "새 할 일"). 할 일마다 알림 1개이고
     * 같은 알림을 오늘 이미 쌓았으면 건너뛴다. 직원 개인 계정만 받는다(매장 계정이 맡은 할 일은 오늘 할 일 화면에서 본다).
     */
    @Transactional
    public int notifyDueSaleTasks(LocalDate today) {
        int sent = 0;
        for (FollowUpTask task : taskRepository.findDueOn(EnumSet.of(FollowUpKind.SALE), FollowUpTaskStatus.OPEN, today)) {
            Staff staff = staffRepository.findById(task.getStaffId()).orElse(null);
            if (staff == null || !staff.isActive() || !EMPLOYEE_RANKS.contains(staff.getRole())) {
                continue;
            }
            String message = ReminderMessages.saleTaskDue(task.getCustomer().getName(), task.getRule().getOffsetAmount(),
                    task.getRule().getOffsetUnit(), task.getRuleName());
            if (notifier.sendOncePerDay(staff.getId(), NotificationType.FOLLOW_UP, task.getCustomer().getId(), task.getId(),
                    message)) {
                sent++;
            }
        }
        log.info("due task notifications sent count={}", sent);
        return sent;
    }

    private static boolean wants(NotifyTiming timing, boolean tomorrow) {
        return tomorrow ? timing.includesDayBefore() : timing.includesSameDay();
    }
}

package com.crmpos.common.service;

import com.crmpos.common.domain.NotificationType;
import com.crmpos.common.domain.PushSubscription;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.repository.PushSubscriptionRepository;
import com.crmpos.common.repository.StaffNotificationRepository;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.web.KstTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함에 쌓고 그 직원의 모든 푸시 구독으로도 보낸다. 푸시가 실패해도 알림함은 그대로다. 만료(GONE)된 구독은 지운다.
 * 로그에는 직원 ID·알림 ID·결과 개수만 남긴다 (문구에 고객 이름이 있다, NFR-04b).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxStaffNotifier implements StaffNotifier {

    private final StaffRepository staffRepository;
    private final StaffNotificationRepository notificationRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSender pushSender;

    @Override
    @Transactional
    public void send(Long staffId, NotificationType type, Long customerId, Long refId, String message) {
        store(staffId, type, customerId, refId, message);
    }

    /** 같은 직원·종류·대상(ref)의 알림을 오늘 이미 쌓았으면 건너뛴다 (DB UNIQUE가 막는다). */
    @Override
    @Transactional
    public boolean sendOncePerDay(Long staffId, NotificationType type, Long customerId, Long refId, String message) {
        return store(staffId, type, customerId, refId, message);
    }

    /** 알림함에 넣고 새로 들어간 경우에만 푸시를 보낸다. 이미 있어 건너뛰었으면 false. */
    private boolean store(Long staffId, NotificationType type, Long customerId, Long refId, String message) {
        Staff staff = staffRepository.findById(staffId).orElse(null);
        if (staff == null || !staff.isActive()) {
            return false;
        }
        int inserted = notificationRepository.insertIfAbsent(staffId, type.name(), customerId, refId, message, now());
        if (inserted == 0) {
            log.info("notification skipped (already exists) staffId={}", staffId);
            return false;
        }
        push(staff.getId(), message);
        log.info("notification sent staffId={}", staffId);
        return true;
    }

    private void push(Long staffId, String message) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByStaffId(staffId);
        int sent = 0;
        for (PushSubscription subscription : subscriptions) {
            PushSender.Result result = pushSender.send(subscription, message);
            if (result == PushSender.Result.SENT) {
                sent++;
            } else if (result == PushSender.Result.GONE) {
                subscriptionRepository.delete(subscription);
            }
        }
        if (!subscriptions.isEmpty()) {
            log.info("push result staffId={} subscriptions={} sent={}", staffId, subscriptions.size(), sent);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}

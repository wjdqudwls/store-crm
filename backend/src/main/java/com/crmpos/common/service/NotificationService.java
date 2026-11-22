package com.crmpos.common.service;

import com.crmpos.common.controller.NotificationInbox;
import com.crmpos.common.controller.NotificationItem;
import com.crmpos.common.controller.NotificationSettingsResponse;
import com.crmpos.common.controller.PushSubscriptionRequest;
import com.crmpos.common.domain.NotifyTiming;
import com.crmpos.common.domain.PushSubscription;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffNotification;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.PushSubscriptionRepository;
import com.crmpos.common.repository.StaffNotificationRepository;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.KstTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직원 개인 웹앱의 알림함·알림 설정·웹 푸시 구독 (CAL-08·09·12, 03-api.md 3.7). 직원 개인 계정(MANAGER~STAFF)만 쓴다.
 * 남의 알림은 읽음 처리할 수 없다(403).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final StaffNotificationRepository notificationRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public NotificationInbox inbox(CurrentAccount account, int page, int size) {
        requireEmployee(account);
        Page<StaffNotification> result = notificationRepository.findInbox(account.id(), PageRequest.of(page, size));
        List<NotificationItem> items = result.getContent().stream().map(NotificationItem::from).toList();
        return new NotificationInbox(notificationRepository.countByStaffIdAndReadAtIsNull(account.id()), items, page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    /** 검사 순서: 없음 404 → 남의 것 403. 이미 읽은 알림은 그대로 둔다. */
    @Transactional
    public NotificationItem read(Long notificationId, CurrentAccount account) {
        requireEmployee(account);
        StaffNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!notification.getStaff().getId().equals(account.id())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        notification.markRead(now());
        return NotificationItem.from(notification);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse settings(CurrentAccount account) {
        requireEmployee(account);
        Staff staff = staffRepository.findById(account.id()).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        return new NotificationSettingsResponse(staff.getNotifyTiming(), subscriptionRepository.existsByStaffId(account.id()));
    }

    @Transactional
    public NotificationSettingsResponse changeTiming(CurrentAccount account, NotifyTiming timing) {
        requireEmployee(account);
        Staff staff = staffRepository.findById(account.id()).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        staff.changeNotifyTiming(timing);
        return new NotificationSettingsResponse(staff.getNotifyTiming(), subscriptionRepository.existsByStaffId(account.id()));
    }

    /** 이 브라우저의 구독을 저장한다. 같은 주소가 이미 있으면 주인과 키를 새 값으로 바꾼다. */
    @Transactional
    public void subscribe(CurrentAccount account, PushSubscriptionRequest request) {
        requireEmployee(account);
        Staff staff = staffRepository.findById(account.id()).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        subscriptionRepository.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existing -> existing.renew(staff, request.keys().p256dh(), request.keys().auth()),
                () -> subscriptionRepository.save(PushSubscription.of(staff, request.endpoint(), request.keys().p256dh(),
                        request.keys().auth(), now())));
    }

    /** 이 브라우저의 구독을 지운다(푸시 끄기). 없는 구독을 지워도 오류가 아니다. */
    @Transactional
    public void unsubscribe(CurrentAccount account, String endpoint) {
        requireEmployee(account);
        subscriptionRepository.deleteByStaffIdAndEndpoint(account.id(), endpoint);
    }

    private static void requireEmployee(CurrentAccount account) {
        account.requireRole(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}

package com.crmpos.common.repository;

import com.crmpos.common.domain.NotificationType;
import com.crmpos.common.domain.StaffNotification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffNotificationRepository extends JpaRepository<StaffNotification, Long> {

    /** 내 알림함: 안 읽은 것 먼저, 그 안에서 최신순 (CAL-09). */
    @Query(value = "select n from StaffNotification n where n.staff.id = :staffId "
            + "order by case when n.readAt is null then 0 else 1 end, n.createdAt desc, n.id desc",
            countQuery = "select count(n) from StaffNotification n where n.staff.id = :staffId")
    Page<StaffNotification> findInbox(@Param("staffId") Long staffId, Pageable pageable);

    long countByStaffIdAndReadAtIsNull(Long staffId);

    /** 같은 문구의 알림을 오늘 이미 쌓았는가 (9시 알림을 다시 돌려도 겹치지 않게). */
    boolean existsByStaffIdAndTypeAndMessageAndCreatedAtGreaterThanEqual(Long staffId, NotificationType type,
            String message, LocalDateTime since);
}

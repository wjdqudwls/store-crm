package com.crmpos.common.repository;

import com.crmpos.common.domain.StaffNotification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffNotificationRepository extends JpaRepository<StaffNotification, Long> {

    /** 내 알림함: 안 읽은 것 먼저, 그 안에서 최신순 (CAL-09). */
    @Query(value = "select n from StaffNotification n where n.staff.id = :staffId "
            + "order by case when n.readAt is null then 0 else 1 end, n.createdAt desc, n.id desc",
            countQuery = "select count(n) from StaffNotification n where n.staff.id = :staffId")
    Page<StaffNotification> findInbox(@Param("staffId") Long staffId, Pageable pageable);

    long countByStaffIdAndReadAtIsNull(Long staffId);

    /**
     * 알림을 넣는다. 같은 직원·종류·대상(ref)·날짜의 알림이 이미 있으면(UNIQUE) 건너뛰고 0을 돌려준다 (배포 중 앱이 둘 떠서
     * 9시 작업이 겹쳐도 하나만 쌓인다). 넣었으면 1.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO staff_notification (staff_id, type, customer_id, ref_id, message, created_at) "
            + "VALUES (:staffId, :type, :customerId, :refId, :message, :createdAt)", nativeQuery = true)
    int insertIfAbsent(@Param("staffId") Long staffId, @Param("type") String type, @Param("customerId") Long customerId,
            @Param("refId") Long refId, @Param("message") String message, @Param("createdAt") LocalDateTime createdAt);
}

package com.crmpos.common.repository;

import com.crmpos.common.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByStaffId(Long staffId);

    boolean existsByStaffId(Long staffId);

    long deleteByStaffIdAndEndpoint(Long staffId, String endpoint);
}

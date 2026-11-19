package com.crmpos.customer.repository;

import com.crmpos.customer.domain.GradeChangeReason;
import com.crmpos.customer.domain.GradeHistory;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeHistoryRepository extends JpaRepository<GradeHistory, Long> {

    /** 마지막 정기 산정(임시 재계산 포함) 이력: 임시·확정 등급의 근거 (GRD-03). */
    Optional<GradeHistory> findFirstByCustomerIdAndReasonInOrderByCreatedAtDescIdDesc(Long customerId,
            Collection<GradeChangeReason> reasons);
}

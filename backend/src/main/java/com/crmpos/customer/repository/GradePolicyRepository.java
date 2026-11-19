package com.crmpos.customer.repository;

import com.crmpos.customer.domain.GradePolicy;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradePolicyRepository extends JpaRepository<GradePolicy, Long> {

    /** 적용 중인 등급 기준: 적용 시작일이 오늘 이전인 것 중 가장 최근 버전 (ADR-027). */
    Optional<GradePolicy> findFirstByAppliesFromLessThanEqualOrderByVersionDesc(LocalDate date);
}

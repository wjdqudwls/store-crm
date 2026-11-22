package com.crmpos.customer.repository;

import com.crmpos.customer.domain.GradeRule;
import com.crmpos.customer.domain.GradeRuleKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRuleRepository extends JpaRepository<GradeRule, Long> {

    List<GradeRule> findByPolicyIdAndKind(Long policyId, GradeRuleKind kind);
}

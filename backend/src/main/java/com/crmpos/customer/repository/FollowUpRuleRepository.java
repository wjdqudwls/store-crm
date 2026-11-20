package com.crmpos.customer.repository;

import com.crmpos.customer.domain.FollowUpRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpRuleRepository extends JpaRepository<FollowUpRule, Long> {

    List<FollowUpRule> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<FollowUpRule> findAllByOrderBySortOrderAscIdAsc();
}

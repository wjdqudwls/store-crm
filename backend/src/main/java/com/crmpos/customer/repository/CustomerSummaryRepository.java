package com.crmpos.customer.repository;

import com.crmpos.customer.domain.CustomerSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSummaryRepository extends JpaRepository<CustomerSummary, Long> {
}

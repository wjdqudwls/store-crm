package com.crmpos.sales.repository;

import com.crmpos.sales.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
}

package com.crmpos.sales.repository;

import com.crmpos.sales.domain.StoreDayClose;
import com.crmpos.sales.domain.StoreDayCloseId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreDayCloseRepository extends JpaRepository<StoreDayClose, StoreDayCloseId> {
}

package com.crmpos.sales.repository;

import com.crmpos.sales.domain.SaleReturn;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    Optional<SaleReturn> findByIdempotencyKey(String idempotencyKey);
}

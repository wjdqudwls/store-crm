package com.crmpos.sales.repository;

import com.crmpos.sales.domain.Sale;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);
}

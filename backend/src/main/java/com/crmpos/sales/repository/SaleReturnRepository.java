package com.crmpos.sales.repository;

import com.crmpos.sales.domain.SaleReturn;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    Optional<SaleReturn> findByIdempotencyKey(String idempotencyKey);

    /** 한 판매의 반품·교환·기록 취소를 오래된 순으로 (새 판매 포함). */
    @Query("select r from SaleReturn r left join fetch r.newSale where r.sale.id = :saleId order by r.returnedAt asc, r.id asc")
    List<SaleReturn> findBySaleId(@Param("saleId") Long saleId);
}

package com.crmpos.sales.repository;

import com.crmpos.sales.domain.Sale;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    /**
     * 판매 한 건을 잠그고(SELECT … FOR UPDATE) 읽는다. 반품·교환·정정·기록 취소는 이 잠금 안에서 남은 수량을 확인해,
     * 다른 키로 동시에 와도 반품 합계가 판매 수량을 넘지 않게 한다 (ADR-030).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findForUpdate(@Param("id") Long id);

    /** 판매한 매장만 가볍게 읽는다. 권한(다른 매장 판매인지)을 입력 검사보다 먼저 확인할 때 쓴다. */
    @Query("select s.storeId from Sale s where s.id = :id")
    Optional<Long> findStoreId(@Param("id") Long id);
}

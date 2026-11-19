package com.crmpos.sales.repository;

import com.crmpos.sales.domain.Sale;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** 판매 내역: 한 매장의 기간 안 판매를 최신순으로 (기록 취소도 status로 보인다). */
    @Query(value = "select s from Sale s left join fetch s.originSale "
            + "where s.storeId = :storeId and s.soldAt >= :from and s.soldAt < :to order by s.soldAt desc, s.id desc",
            countQuery = "select count(s) from Sale s where s.storeId = :storeId and s.soldAt >= :from and s.soldAt < :to")
    Page<Sale> findHistory(@Param("storeId") Long storeId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, Pageable pageable);

    /** 하루 판매 수: 기록 취소를 뺀 영수증 수를 매장별로 센다. */
    @Query("select s.storeId, count(s) from Sale s where s.soldAt >= :from and s.soldAt < :to "
            + "and s.status <> com.crmpos.sales.domain.SaleStatus.VOIDED group by s.storeId")
    List<Object[]> countByStore(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 영수증 상세: 라인과 원 판매까지 한 번에. */
    @Query("select distinct s from Sale s left join fetch s.lines left join fetch s.originSale where s.id = :id")
    Optional<Sale> findDetailById(@Param("id") Long id);

    /** 고객 구매이력의 한 쪽(ID만): 기록 취소를 뺀 영수증, keyword(소문자·LIKE 이스케이프 '!')가 있으면 상품명·품번에 든 줄이 있는 것. */
    @Query(value = "select s.id from Sale s where s.customerId = :customerId "
            + "and s.status <> com.crmpos.sales.domain.SaleStatus.VOIDED "
            + "and (:keyword is null or exists (select 1 from SaleLine l where l.sale = s and "
            + "(lower(l.productName) like :keyword escape '!' or lower(l.productCode) like :keyword escape '!'))) "
            + "order by s.soldAt desc, s.id desc",
            countQuery = "select count(s) from Sale s where s.customerId = :customerId "
            + "and s.status <> com.crmpos.sales.domain.SaleStatus.VOIDED "
            + "and (:keyword is null or exists (select 1 from SaleLine l where l.sale = s and "
            + "(lower(l.productName) like :keyword escape '!' or lower(l.productCode) like :keyword escape '!')))")
    Page<Long> findPurchaseIds(@Param("customerId") Long customerId, @Param("keyword") String keyword,
            Pageable pageable);

    @Query("select distinct s from Sale s left join fetch s.lines where s.id in :ids")
    List<Sale> findWithLinesByIdIn(@Param("ids") java.util.Collection<Long> ids);
}

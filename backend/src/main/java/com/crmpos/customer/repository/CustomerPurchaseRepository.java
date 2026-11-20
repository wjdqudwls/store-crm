package com.crmpos.customer.repository;

import com.crmpos.customer.domain.CustomerPurchase;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerPurchaseRepository extends JpaRepository<CustomerPurchase, Long> {

    Optional<CustomerPurchase> findBySaleLineId(Long saleLineId);

    /** 같은 구매 건(처음 판매)의 가장 이른 실적 시각. 교환·정정이 처음 판매 시각을 물려받는 데 쓴다. */
    @Query("select min(p.countedAt) from CustomerPurchase p where p.purchaseGroupId = :groupId")
    Optional<LocalDateTime> findCountedAtOfGroup(@Param("groupId") Long groupId);

    /** 기록 취소된 수량을 뺀 뒤 상품이 남은 구매의 첫·마지막 실적 시각 (BR-09: 반품은 방문, 기록 취소는 방문이 아님). */
    @Query("select new com.crmpos.customer.repository.VisitRange(min(p.countedAt), max(p.countedAt)) "
            + "from CustomerPurchase p where p.customer.id = :customerId and p.qty > p.voidedQty")
    VisitRange findVisitRange(@Param("customerId") Long customerId);

    /** 한 고객의 이 구매 건에 상품이 남아 있는가 (줄 하나라도 산 수량이 나간 수량보다 많으면 남은 것, BR-02). */
    @Query("select count(p) > 0 from CustomerPurchase p where p.purchaseGroupId = :groupId "
            + "and p.customer.id = :customerId and p.qty > p.returnedQty")
    boolean existsRemaining(@Param("groupId") Long groupId, @Param("customerId") Long customerId);

    /** 한 고객의 이 구매 건에 속한 판매 ID들 (처음 판매·교환·정정 판매). 구매 건의 할 일을 찾는 데 쓴다. */
    @Query("select distinct p.saleId from CustomerPurchase p where p.purchaseGroupId = :groupId "
            + "and p.customer.id = :customerId")
    List<Long> findSaleIdsOfGroup(@Param("groupId") Long groupId, @Param("customerId") Long customerId);

    @Query("select new com.crmpos.customer.repository.SaleSoldAt(p.saleId, min(p.soldAt)) from CustomerPurchase p "
            + "where p.saleId in :saleIds group by p.saleId")
    List<SaleSoldAt> findSoldAtOfSales(@Param("saleIds") Collection<Long> saleIds);
}

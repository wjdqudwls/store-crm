package com.crmpos.customer.repository;

import com.crmpos.customer.domain.CustomerPurchase;
import java.time.LocalDateTime;
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
}

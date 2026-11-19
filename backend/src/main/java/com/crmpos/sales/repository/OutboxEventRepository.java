package com.crmpos.sales.repository;

import com.crmpos.sales.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * 지금 보낼 수 있는 이벤트: PENDING이고, 같은 판매(aggregate)에 앞선 이벤트가 PUBLISHED가 아닌 채로 남아 있지 않은 것.
     * 오래된 순이다 (ADR-007). 막힌 판매의 이벤트는 후보에서 빠져 다른 판매를 굶기지 않는다.
     */
    @Query("""
            select e from OutboxEvent e
            where e.status = com.crmpos.sales.domain.OutboxStatus.PENDING
              and not exists (
                  select 1 from OutboxEvent b
                  where b.aggregateId = e.aggregateId
                    and b.status <> com.crmpos.sales.domain.OutboxStatus.PUBLISHED
                    and (b.createdAt < e.createdAt or (b.createdAt = e.createdAt and b.eventId < e.eventId)))
            order by e.createdAt asc, e.eventId asc
            """)
    List<OutboxEvent> findDeliverable(Pageable pageable);

    /** 한 이벤트를 잠그고 읽는다. 겹쳐 도는 발행기가 같은 이벤트를 두 번 보내지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.eventId = :eventId")
    Optional<OutboxEvent> findForUpdate(@Param("eventId") String eventId);

    /** 같은 판매에 앞선 이벤트가 아직 PUBLISHED가 아닌지 (잠근 뒤 다시 확인할 때 쓴다). */
    @Query("""
            select count(b) > 0 from OutboxEvent b
            where b.aggregateId = :aggregateId
              and b.status <> com.crmpos.sales.domain.OutboxStatus.PUBLISHED
              and (b.createdAt < :createdAt or (b.createdAt = :createdAt and b.eventId < :eventId))
            """)
    boolean existsEarlierUnpublished(@Param("aggregateId") Long aggregateId,
            @Param("createdAt") java.time.LocalDateTime createdAt, @Param("eventId") String eventId);
}

package com.crmpos.customer.repository;

import com.crmpos.customer.domain.FollowUpKind;
import com.crmpos.customer.domain.FollowUpTask;
import com.crmpos.customer.domain.FollowUpTaskStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, Long> {

    /** 고객의 열린 판매 할 일 (새 구매가 생기면 대체한다). */
    List<FollowUpTask> findByCustomerIdAndKindAndStatus(Long customerId, FollowUpKind kind, FollowUpTaskStatus status);

    /** 이 판매들을 기준으로 한 한 고객의 열린 판매 할 일 (구매 건의 상품이 모두 나가면 취소한다). */
    List<FollowUpTask> findByCustomerIdAndSaleIdInAndStatus(Long customerId, Collection<Long> saleIds,
            FollowUpTaskStatus status);

    /** 그날 만기인 열린 할 일 (알림에 쓴다). 고객 이름과 단계를 함께 읽는다. */
    @Query("select t from FollowUpTask t join fetch t.customer left join fetch t.rule where t.kind in :kinds and t.status = :status "
            + "and t.dueDate = :date order by t.id")
    List<FollowUpTask> findDueOn(@Param("kinds") Collection<FollowUpKind> kinds,
            @Param("status") FollowUpTaskStatus status, @Param("date") LocalDate date);

    List<FollowUpTask> findBySourceNoteIdIn(Collection<Long> noteIds);

    List<FollowUpTask> findByNoteIdIn(Collection<Long> noteIds);

    /** 오늘 할 일: 만기일이 오늘이거나 지난 열린 할 일 (정렬은 Pageable로: 만기일 오래된 순). */
    @Query("select t from FollowUpTask t join fetch t.customer where t.status = :status and t.dueDate <= :today "
            + "and t.staffId in :staffIds")
    Page<FollowUpTask> findDue(@Param("status") FollowUpTaskStatus status, @Param("today") LocalDate today,
            @Param("staffIds") Collection<Long> staffIds, Pageable pageable);

    /** 고객 상세의 할 일: 열린 것 먼저, 그 안에서 만기일 순. */
    @Query(value = "select t from FollowUpTask t where t.customer.id = :customerId "
            + "order by case when t.status = com.crmpos.customer.domain.FollowUpTaskStatus.OPEN then 0 else 1 end, "
            + "t.dueDate asc, t.id asc",
            countQuery = "select count(t) from FollowUpTask t where t.customer.id = :customerId")
    Page<FollowUpTask> findOfCustomer(@Param("customerId") Long customerId, Pageable pageable);
}

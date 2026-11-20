package com.crmpos.customer.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.FollowUpKind;
import com.crmpos.customer.domain.FollowUpPlanner;
import com.crmpos.customer.domain.FollowUpRule;
import com.crmpos.customer.domain.FollowUpTask;
import com.crmpos.customer.domain.FollowUpTaskStatus;
import com.crmpos.customer.event.SaleCompletedEvent;
import com.crmpos.customer.repository.CustomerPurchaseRepository;
import com.crmpos.customer.repository.FollowUpRuleRepository;
import com.crmpos.customer.repository.FollowUpTaskRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 판매 이벤트를 처리할 때 후속 연락 할 일을 만들고 닫는다 (BR-14, CAL-06). SaleEventService의 트랜잭션 안에서 불린다.
 * 날짜·받는 사람·최근 구매 판단 같은 규칙은 {@link FollowUpPlanner}(순수 자바)가 하고, 여기서는 저장만 한다.
 */
@Service
@RequiredArgsConstructor
public class FollowUpPlanService {

    private final FollowUpTaskRepository taskRepository;
    private final FollowUpRuleRepository ruleRepository;
    private final StaffRepository staffRepository;
    private final CustomerPurchaseRepository purchaseRepository;

    /**
     * 회원 판매 하나로 단계별 할 일을 만든다. 교환은 만들지 않는다. 그 구매가 고객의 가장 최근 구매일 때만 만들고,
     * 같은 고객의 이전 열린 판매 할 일은 대체(REPLACED)한다. 정정이면 만기일이 이미 지난 단계는 만들지 않는다.
     * 메모에서 만든 방문 예약·재연락(VISIT·RECALL)은 새 구매로 닫지 않는다.
     *
     * @param countedAt 이 구매의 실적 시각 (구매 건의 처음 판매 시각)
     * @param lastVisitAt 이 구매를 반영한 뒤의 고객 마지막 방문일
     */
    public void onSaleCompleted(Customer customer, SaleCompletedEvent event, LocalDateTime soldAt,
            LocalDateTime countedAt, LocalDateTime lastVisitAt) {
        if ("EXCHANGE".equals(event.kind())) {
            return;
        }
        if (!FollowUpPlanner.isLatestPurchase(countedAt, lastVisitAt)) {
            return;
        }
        LocalDateTime now = now();
        for (FollowUpTask previous : taskRepository.findByCustomerIdAndKindAndStatus(customer.getId(),
                FollowUpKind.SALE, FollowUpTaskStatus.OPEN)) {
            previous.replace(now);
        }

        Long assignee = chooseAssignee(customer, event.staffId());
        boolean correction = "CORRECTION".equals(event.kind());
        LocalDate today = LocalDate.now(KstTime.ZONE);
        for (FollowUpRule rule : ruleRepository.findByActiveTrueOrderBySortOrderAscIdAsc()) {
            LocalDate due = FollowUpPlanner.dueDate(soldAt.toLocalDate(), rule.getOffsetAmount(), rule.getOffsetUnit());
            if (FollowUpPlanner.shouldCreate(correction, due, today)) {
                taskRepository.save(FollowUpTask.forSale(customer, assignee, event.saleId(), rule, due, now));
            }
        }
    }

    /**
     * 반품·기록 취소로 이 고객의 구매 건에 상품이 하나도 안 남으면 그 구매 건의 열린 판매 할 일을 취소한다.
     * 일부만 나가거나 교환 상품이 남아 있으면 그대로 둔다 (BR-14).
     */
    public void onPurchaseGroupsReduced(Long customerId, Set<Long> purchaseGroupIds) {
        LocalDateTime now = now();
        for (Long groupId : purchaseGroupIds) {
            if (purchaseRepository.existsRemaining(groupId, customerId)) {
                continue;
            }
            List<Long> saleIds = purchaseRepository.findSaleIdsOfGroup(groupId, customerId);
            for (FollowUpTask task : taskRepository.findByCustomerIdAndSaleIdInAndStatus(customerId, saleIds,
                    FollowUpTaskStatus.OPEN)) {
                task.cancel(now);
            }
        }
    }

    private Long chooseAssignee(Customer customer, Long saleStaffId) {
        Long customerStaffId = customer.getStaffId();
        boolean active = customerStaffId != null
                && staffRepository.findById(customerStaffId).map(Staff::isActive).orElse(false);
        return FollowUpPlanner.chooseAssignee(customerStaffId, active, saleStaffId);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}

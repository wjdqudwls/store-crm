package com.crmpos.customer.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.controller.CallListItem;
import com.crmpos.customer.domain.CallListFilter;
import com.crmpos.customer.domain.CallListScope;
import com.crmpos.customer.domain.GradePolicy;
import com.crmpos.customer.domain.GradeRuleKind;
import com.crmpos.customer.domain.PhoneNumber;
import com.crmpos.customer.repository.CallListQueryMapper;
import com.crmpos.customer.repository.CallListRow;
import com.crmpos.customer.repository.GradePolicyRepository;
import com.crmpos.customer.repository.GradeRuleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콜리스트 (CAL-01, BR-09, 03-api.md 3.3). 조건 기본값은 {@link CallListFilter}가 정하고, 여기서는 매장 범위·최소 등급을
 * 실제 값으로 바꿔 MyBatis 쿼리에 넘긴다. 매장 범위는 직원·매장 계정은 자기 매장만, 본사는 storeId가 필수다.
 */
@Service
@RequiredArgsConstructor
public class CallListService {

    private final CallListQueryMapper mapper;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final GradePolicyRepository policyRepository;
    private final GradeRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public PageResponse<CallListItem> list(CurrentAccount account, CallListFilter.Request request, Long storeIdParam,
            int page, int size) {
        Long storeId = resolveStore(account, storeIdParam);
        CallListFilter filter = CallListFilter.resolve(account.role(), account.id(), request);

        // MINE은 담당 직원만 보고 매장 범위를 따로 걸지 않는다. STORE는 그 매장 고객 전체다
        Long scopeStore = filter.scope() == CallListScope.STORE ? storeId : null;
        List<Long> storeAccounts = scopeStore == null ? List.of() : staffRepository.findByStoreIdOrderByIdAsc(scopeStore)
                .stream().filter(s -> s.getRole() == StaffRole.STORE).map(Staff::getId).toList();
        List<GradeCode> grades = CallListFilter.gradesAtLeast(filter.minGrade(), cutScores());
        LocalDateTime inactiveBefore = filter.inactiveDays() == null || filter.inactiveDays() <= 0 ? null
                : LocalDateTime.now(KstTime.ZONE).minusDays(filter.inactiveDays());

        List<CallListRow> rows = mapper.find(scopeStore, storeAccounts, filter.staffId(), filter.promoOnly(), grades,
                inactiveBefore, filter.target(), filter.itemType(), filter.memberRelation(), size, page * size);
        long total = mapper.count(scopeStore, storeAccounts, filter.staffId(), filter.promoOnly(), grades,
                inactiveBefore, filter.target(), filter.itemType(), filter.memberRelation());

        Map<Long, String> names = new HashMap<>();
        staffRepository.findAllById(rows.stream().map(CallListRow::staffId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet())).forEach(s -> names.put(s.getId(), s.getName()));
        List<CallListItem> items = rows.stream().map(r -> new CallListItem(r.id(), r.name(),
                PhoneNumber.masked(r.phoneLast4()), r.grade(), r.lastVisitAt() == null ? null : KstTime.toOffset(r.lastVisitAt()),
                KstTime.toOffset(r.createdAt()), r.staffId() == null ? null : names.get(r.staffId()),
                r.marketingAllowed())).toList();
        return PageResponse.of(items, page, size, total);
    }

    /** 직원·매장 계정은 자기 매장(다른 매장을 지정하면 403), 본사는 storeId가 필수다(없으면 400, 없는 매장 404). */
    private Long resolveStore(CurrentAccount account, Long storeIdParam) {
        if (account.role() == StaffRole.HQ) {
            if (storeIdParam == null) {
                throw new ValidationFailedException(List.of(new FieldError("storeId", "매장을 골라 주세요.")));
            }
            if (!storeRepository.existsById(storeIdParam)) {
                throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
            }
            return storeIdParam;
        }
        if (storeIdParam != null && !storeIdParam.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return account.storeId();
    }

    /** 적용 중인 등급 기준의 등급 컷 점수 (BR-04). 기준이 없으면 빈 맵이라 최소 등급 하나만 남는다. */
    private Map<GradeCode, Integer> cutScores() {
        Map<GradeCode, Integer> cuts = new EnumMap<>(GradeCode.class);
        GradePolicy policy = policyRepository
                .findFirstByAppliesFromLessThanEqualOrderByVersionDesc(LocalDate.now(KstTime.ZONE)).orElse(null);
        if (policy != null) {
            ruleRepository.findByPolicyIdAndKind(policy.getId(), GradeRuleKind.GRADE)
                    .forEach(r -> cuts.put(r.getGradeCode(), (int) r.getMinValue()));
        }
        return cuts;
    }
}

package com.crmpos.customer.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.BodyValidation;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.controller.FollowUpRuleItem;
import com.crmpos.customer.controller.FollowUpRuleRequest;
import com.crmpos.customer.domain.FollowUpRule;
import com.crmpos.customer.repository.FollowUpRuleRepository;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후속 연락 단계 설정 (CAL-07, BR-14). 본사가 바꾸고, 바꾼 설정은 이후 구매부터 적용된다(이미 만든 할 일은 복사본이라 그대로).
 * 단계는 지우지 않고 끄기만 한다.
 */
@Service
@RequiredArgsConstructor
public class FollowUpRuleService {

    private static final int MAX_RULES = 10;

    private final FollowUpRuleRepository ruleRepository;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<FollowUpRuleItem> list() {
        return ruleRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(FollowUpRuleItem::from).toList();
    }

    /** 전체 저장: id가 있으면 고치고 없으면 만든다. 목록에 없는 기존 단계는 끈다. 순서는 목록 순서다. */
    @Transactional
    public List<FollowUpRuleItem> save(List<FollowUpRuleRequest> requests, CurrentAccount account) {
        account.requireRole(StaffRole.HQ);
        List<FieldError> errors = new ArrayList<>();
        if (requests == null || requests.isEmpty() || requests.size() > MAX_RULES) {
            errors.add(new FieldError("rules", "단계는 1개 이상 " + MAX_RULES + "개 이하로 넣어 주세요."));
        } else {
            for (int i = 0; i < requests.size(); i++) {
                int index = i;
                if (requests.get(i) == null) {
                    errors.add(new FieldError("rules[" + index + "]", "단계 내용을 넣어 주세요."));
                    continue;
                }
                BodyValidation.violations(validator, requests.get(i))
                        .forEach(e -> errors.add(new FieldError("rules[" + index + "]." + e.field(), e.reason())));
            }
        }
        Map<Long, FollowUpRule> existing = new HashMap<>();
        ruleRepository.findAll().forEach(r -> existing.put(r.getId(), r));
        if (requests != null) {
            for (int i = 0; i < requests.size(); i++) {
                if (requests.get(i) == null) {
                    continue;
                }
                Long id = requests.get(i).id();
                if (id != null && !existing.containsKey(id)) {
                    errors.add(new FieldError("rules[" + i + "].id", "없는 단계입니다."));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        Set<Long> kept = new HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            FollowUpRuleRequest r = requests.get(i);
            int order = i + 1;
            if (r.id() == null) {
                ruleRepository.save(FollowUpRule.create(r.name().trim(), r.amount(), r.unit(), r.promotional(), order,
                        r.enabled(), now));
            } else {
                existing.get(r.id()).apply(r.name().trim(), r.amount(), r.unit(), r.promotional(), order, r.enabled(), now);
                kept.add(r.id());
            }
        }
        existing.values().stream().filter(r -> !kept.contains(r.getId()) && r.isActive()).forEach(r -> r.disable(now));
        ruleRepository.flush();
        return list();
    }
}

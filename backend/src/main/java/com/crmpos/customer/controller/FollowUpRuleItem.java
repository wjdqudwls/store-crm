package com.crmpos.customer.controller;

import com.crmpos.customer.domain.FollowUpOffsetUnit;
import com.crmpos.customer.domain.FollowUpRule;

/** 후속 연락 단계 설정 항목 (03-api.md 3.6, CAL-07). amount·unit은 판매일로부터의 기간, enabled는 켜짐 여부다. */
public record FollowUpRuleItem(Long id, String name, int amount, FollowUpOffsetUnit unit, boolean promotional,
        boolean enabled) {

    public static FollowUpRuleItem from(FollowUpRule rule) {
        return new FollowUpRuleItem(rule.getId(), rule.getName(), rule.getOffsetAmount(), rule.getOffsetUnit(),
                rule.isPromotional(), rule.isActive());
    }
}

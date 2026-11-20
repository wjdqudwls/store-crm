package com.crmpos.customer.controller;

import com.crmpos.customer.domain.FollowUpOffsetUnit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 단계 설정 한 줄. id가 있으면 그 단계를 고치고 없으면 새 단계다. 목록에 없는 기존 단계는 꺼진다 (지우지 않는다). */
public record FollowUpRuleRequest(Long id, @NotBlank @Size(max = 50) String name, @Min(1) @Max(36) int amount,
        @NotNull FollowUpOffsetUnit unit, boolean promotional, boolean enabled) {
}

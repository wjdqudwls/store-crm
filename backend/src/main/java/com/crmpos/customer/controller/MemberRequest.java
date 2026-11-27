package com.crmpos.customer.controller;

import com.crmpos.common.type.Gender;
import com.crmpos.customer.domain.MemberRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 가족 구성원 추가·수정 본문 (03-api.md 3.2, CUS-05). 수정도 같은 본문으로 전체를 바꾼다. 호칭은 이름일 수 있어 toString은 가린다. */
public record MemberRequest(
        @NotBlank @Size(max = 30) String nickname,
        @NotNull MemberRelation relation,
        Gender gender,
        Integer birthYear,
        CustomerRegisterRequest.Sizes sizes) {

    @Override
    public String toString() {
        return "MemberRequest[개인정보 가림]";
    }
}

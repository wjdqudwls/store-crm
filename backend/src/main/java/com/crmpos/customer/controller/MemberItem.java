package com.crmpos.customer.controller;

import com.crmpos.common.type.Gender;
import com.crmpos.customer.domain.CustomerMember;
import com.crmpos.customer.domain.MemberRelation;

/** 가족 구성원 항목 (03-api.md 4.12 모양). */
public record MemberItem(Long id, String nickname, MemberRelation relation, Gender gender, Short birthYear,
        Sizes sizes) {

    public record Sizes(String top, String outer, String bottom, String shoes) {
    }

    public static MemberItem from(CustomerMember m) {
        return new MemberItem(m.getId(), m.getNickname(), m.getRelation(), m.getGender(), m.getBirthYear(),
                new Sizes(m.getSizeTop(), m.getSizeOuter(), m.getSizeBottom(), m.getSizeShoes()));
    }

    @Override
    public String toString() {
        return "MemberItem[id=" + id + "]";
    }
}

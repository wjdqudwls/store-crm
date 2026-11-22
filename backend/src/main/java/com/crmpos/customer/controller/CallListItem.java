package com.crmpos.customer.controller;

import com.crmpos.common.type.GradeCode;
import java.time.OffsetDateTime;

/**
 * 콜리스트 항목 (03-api.md 4.12 "콜리스트 항목"). 구매가 없으면 lastVisitAt이 null이고 화면은 가입일(createdAt)로
 * "가입 후 N일"을 보인다 (BR-09). marketingAllowed가 false면 "홍보 연락 불가"로 표시한다 (BR-08).
 * 이름·번호가 있어 toString은 가린다 (NFR-04b).
 */
public record CallListItem(Long id, String name, String phoneMasked, GradeCode grade, OffsetDateTime lastVisitAt,
        OffsetDateTime createdAt, String staffName, boolean marketingAllowed) {

    @Override
    public String toString() {
        return "CallListItem[id=" + id + "]";
    }
}

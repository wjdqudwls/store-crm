package com.crmpos.customer.repository;

import com.crmpos.common.type.GradeCode;
import java.time.LocalDateTime;

/** 콜리스트 한 줄. 전화번호는 복호화하지 않고 뒷자리 4개만 읽는다 (NFR-05). */
public record CallListRow(Long id, String name, String phoneLast4, GradeCode grade, LocalDateTime lastVisitAt,
        LocalDateTime createdAt, Long staffId, boolean marketingAllowed) {

    @Override
    public String toString() {
        return "CallListRow[id=" + id + "]";
    }
}

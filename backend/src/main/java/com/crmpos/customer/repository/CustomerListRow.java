package com.crmpos.customer.repository;

import com.crmpos.common.type.GradeCode;
import java.time.LocalDateTime;

/** 고객 검색 목록의 한 줄. 전화번호는 복호화하지 않고 뒷자리 4개만 읽는다 (NFR-05). */
public record CustomerListRow(Long id, String name, String phoneLast4, GradeCode grade,
        LocalDateTime lastVisitAt, Long staffId) {

    @Override
    public String toString() {
        return "CustomerListRow[id=" + id + "]";
    }
}

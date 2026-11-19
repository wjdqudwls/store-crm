package com.crmpos.customer.controller;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.domain.PhoneNumber;
import com.crmpos.customer.repository.CustomerListRow;
import java.time.OffsetDateTime;

/** 고객 검색 목록 항목 (03-api.md 4.3). 번호는 뒷자리 4개만 보인다 (NFR-05). */
public record CustomerListItem(Long id, String name, String phoneMasked, GradeCode grade,
        OffsetDateTime lastVisitAt, String staffName) {

    public static CustomerListItem from(CustomerListRow row, String staffName) {
        return new CustomerListItem(row.id(), row.name(), PhoneNumber.masked(row.phoneLast4()), row.grade(),
                KstTime.toOffset(row.lastVisitAt()), staffName);
    }

    @Override
    public String toString() {
        return "CustomerListItem[id=" + id + "]";
    }
}

package com.crmpos.customer.api;

import com.crmpos.common.type.GradeCode;

/** 판매 모듈이 읽는 고객 정보: 판매 당시 등급으로 남길 현재 적용 등급 (BR-10). */
public record SaleCustomer(Long id, GradeCode currentGrade) {
}

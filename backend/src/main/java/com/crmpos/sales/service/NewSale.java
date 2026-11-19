package com.crmpos.sales.service;

import com.crmpos.common.type.GradeCode;
import com.crmpos.sales.domain.SaleLine;
import java.time.LocalDateTime;
import java.util.List;

/** 검증을 끝낸 판매 한 건. {@link SaleWriter}가 영수증 번호를 붙여 저장한다. */
record NewSale(Long storeId, String storeCode, String idempotencyKey, String requestHash, Long sellerStaffId,
        Long recordedBy, Long customerId, GradeCode appliedGrade, LocalDateTime soldAt, String memo,
        List<SaleLine> lines) {
}

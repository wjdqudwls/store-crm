package com.crmpos.sales.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.sales.domain.SaleLine;
import java.util.List;

/** 검증을 끝낸 정정 한 건. {@link CorrectionWriter}가 원 판매를 잠그고 저장한다. */
record NewCorrection(Long saleId, StaffRole role, Long accountStoreId, String storeCode, String idempotencyKey,
        String requestHash, Long staffId, Long recordedBy, Long customerId,
        com.crmpos.common.type.GradeCode currentGrade, List<SaleLine> lines) {
}

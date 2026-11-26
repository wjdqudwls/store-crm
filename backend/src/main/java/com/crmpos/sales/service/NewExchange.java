package com.crmpos.sales.service;

import com.crmpos.sales.controller.ReturnRequest;
import com.crmpos.sales.domain.SaleLine;
import java.util.List;

/** 검증을 끝낸 교환 한 건. {@link ExchangeWriter}가 원 판매를 잠그고 저장한다. */
record NewExchange(Long saleId, Long storeId, String storeCode, String idempotencyKey, String requestHash, Long staffId,
        Long recordedBy, List<ReturnRequest.Line> returnLines, List<SaleLine> newLines) {
}

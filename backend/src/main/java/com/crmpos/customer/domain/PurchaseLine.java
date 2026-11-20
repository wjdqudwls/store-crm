package com.crmpos.customer.domain;

import java.time.LocalDateTime;

/**
 * 등급 계산기가 읽는 구매 한 줄 (customer_purchase의 계산에 필요한 칸만). 계산기는 DB를 모른다 (순수 자바).
 *
 * @param purchaseGroupId 구매 건 = 처음 판매 ID (BR-02). 같은 값이면 한 구매 건이다.
 * @param countedAt       실적에 넣는 시각 (처음 판매 시각, BR-03)
 * @param qty             산 수량
 * @param returnedQty     나간 수량 (반품·교환·기록 취소). voidedQty를 포함한다.
 * @param voidedQty       returnedQty 중 기록 취소분 (없던 구매)
 * @param amount          결제 금액
 * @param returnedAmount  돌려준 금액
 */
public record PurchaseLine(long purchaseGroupId, LocalDateTime countedAt, int qty, int returnedQty, int voidedQty,
        long amount, long returnedAmount) {
}

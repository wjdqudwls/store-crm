package com.crmpos.customer.domain;

import com.crmpos.common.type.GradeCode;

/** 등급 계산 결과 (BR-04). 점수 3개와 근거(티켓 수·순구매금액)를 함께 돌려준다. */
public record GradeResult(int ticketCount, long netAmount, int ticketScore, int amountScore, int totalScore,
        GradeCode grade) {
}

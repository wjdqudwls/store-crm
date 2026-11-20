package com.crmpos.customer.domain;

import com.crmpos.common.type.GradeCode;
import java.util.List;

/**
 * 점수·등급 기준 (BR-04). 본사가 정하는 설정값이라 계산기는 값을 모른다. 목록은 어떤 순서로 와도 된다.
 *
 * @param amountBands 순구매금액 구간: minValue 이상이면 score (원)
 * @param ticketBands 티켓 수 구간: minValue 이상이면 score (건)
 * @param gradeCuts   등급 컷: 합계 점수가 minScore 이상이면 그 등급 (가장 높은 컷이 이긴다)
 */
public record GradeRules(List<Band> amountBands, List<Band> ticketBands, List<Cut> gradeCuts) {

    public record Band(long minValue, int score) {
    }

    public record Cut(GradeCode grade, int minScore) {
    }
}

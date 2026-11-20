package com.crmpos.customer.domain;

import com.crmpos.common.type.GradeCode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 등급 계산기 (GRD-01, BR-02·03·04). 스프링·DB를 모르는 순수 자바다. 구매 줄과 기간과 기준을 받아 등급을 계산한다.
 * 규칙은 [01-requirements.md BR-02~04]를 보고 직접 채운다 (B3-2, 학습용으로 사용자가 구현).
 */
public final class GradeCalculator {

    private GradeCalculator() {
    }

    /**
     * 기간 [periodStart, periodEnd) 안의 구매로 등급을 계산한다 (시작 포함, 끝 미포함).
     * <ul>
     *   <li>기간 판단은 줄의 countedAt으로 한다 (BR-03).</li>
     *   <li>티켓 수 = 기간 안에서 상품이 남은 구매 건(purchaseGroupId) 수. 줄 하나라도 qty - returnedQty > 0이면 그 구매 건은 산 것이다 (BR-02).</li>
     *   <li>순구매금액 = 기간 안 줄의 (amount - returnedAmount) 합 (BR-02·17).</li>
     *   <li>점수 = 금액 점수 + 티켓 점수, 등급 = 합계 점수로 정한 등급 (BR-04).</li>
     * </ul>
     */
    public static GradeResult calculate(List<PurchaseLine> lines, LocalDateTime periodStart, LocalDateTime periodEnd,
            GradeRules rules) {
        throw new UnsupportedOperationException("B3-2: 직접 구현");
    }

    /** 값이 minValue 이상인 구간 중 가장 높은 구간의 점수. 어느 구간에도 안 들면 0. 구간은 순서 없이 와도 된다 (BR-04: 경계는 이상). */
    public static int scoreOf(long value, List<GradeRules.Band> bands) {
        throw new UnsupportedOperationException("B3-2: 직접 구현");
    }

    /** 합계 점수가 minScore 이상인 컷 중 가장 높은 컷의 등급. 어느 컷에도 안 들면 NORMAL. 컷은 순서 없이 와도 된다. */
    public static GradeCode gradeOf(int totalScore, List<GradeRules.Cut> cuts) {
        throw new UnsupportedOperationException("B3-2: 직접 구현");
    }
}

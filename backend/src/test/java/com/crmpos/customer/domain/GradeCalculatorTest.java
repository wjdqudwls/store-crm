package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.type.GradeCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * GradeResult 칸 순서: (티켓 수, 순구매금액, 티켓 점수, 금액 점수, 합계 점수, 등급).
 * 등급 계산기 (GRD-01, BR-02·03·04, NFR-09·10). 규칙은 01-requirements.md BR-02~04를 본다.
 * 모든 경계는 "이상(>=)"이다. 기준값은 GradeTestRules의 데모용 초기값이다.
 */
class GradeCalculatorTest {

    private static final LocalDateTime SEASON_START = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime SEASON_END = LocalDateTime.of(2027, 6, 1, 0, 0);
    private static final LocalDateTime IN_SEASON = LocalDateTime.of(2026, 11, 19, 10, 0);

    private final GradeRules rules = GradeTestRules.demo();

    /** 구매 한 줄: 산 수량 qty, 나간 수량 returned(그중 기록 취소 voided), 금액 amount, 돌려준 금액 refunded. */
    private static PurchaseLine line(long group, LocalDateTime at, int qty, int returned, int voided, long amount,
            long refunded) {
        return new PurchaseLine(group, at, qty, returned, voided, amount, refunded);
    }

    /** 반품 없는 한 줄짜리 구매 건. */
    private static PurchaseLine buy(long group, long amount) {
        return line(group, IN_SEASON, 1, 0, 0, amount, 0);
    }

    private GradeResult calc(List<PurchaseLine> lines) {
        return GradeCalculator.calculate(lines, SEASON_START, SEASON_END, rules);
    }

    // --- 구간별 점수: 경계는 이상(>=) ---

    @ParameterizedTest(name = "순구매금액 {0}원 → 금액 점수 {1}")
    @CsvSource({"0,0", "999999,0", "1000000,10", "1999999,10", "2000000,20", "2999999,20", "3000000,40",
            "3999999,40", "4000000,60", "99999999,60"})
    @DisplayName("BR-04: 금액 구간 경계는 이상이다 (100만원 미만 0점, 100만원 이상 10점 …)")
    void should_scoreAmount_withInclusiveBoundaries(long amount, int expected) {
        assertThat(GradeCalculator.scoreOf(amount, rules.amountBands())).isEqualTo(expected);
    }

    @ParameterizedTest(name = "티켓 {0}건 → 티켓 점수 {1}")
    @CsvSource({"0,0", "1,10", "2,20", "3,30", "4,40", "5,40", "30,40"})
    @DisplayName("BR-04: 티켓 구간 경계 (4건 이상은 40점)")
    void should_scoreTickets_withInclusiveBoundaries(long tickets, int expected) {
        assertThat(GradeCalculator.scoreOf(tickets, rules.ticketBands())).isEqualTo(expected);
    }

    @Test
    @DisplayName("BR-04: 구간 목록이 섞여 있어도 가장 높은 구간으로 계산한다 (기준은 설정값이다)")
    void should_ignoreBandOrder() {
        List<GradeRules.Band> shuffled = new ArrayList<>(rules.amountBands());
        Collections.reverse(shuffled);

        assertThat(GradeCalculator.scoreOf(2_500_000, shuffled)).isEqualTo(20);
    }

    // --- 등급 컷 순서 ---

    @ParameterizedTest(name = "합계 {0}점 → {1}")
    @CsvSource({"0,NORMAL", "19,NORMAL", "20,D", "39,D", "40,C", "69,C", "70,B", "99,B", "100,A", "140,A"})
    @DisplayName("BR-04: 등급 컷 경계는 이상이다 (0~19 일반, 20~39 D, 40~69 C, 70~99 B, 100 A)")
    void should_decideGrade_withInclusiveCuts(int total, GradeCode expected) {
        assertThat(GradeCalculator.gradeOf(total, rules.gradeCuts())).isEqualTo(expected);
    }

    @Test
    @DisplayName("BR-04: 컷 목록이 섞여 있어도 높은 컷이 이긴다 (컷 점수가 높은 등급이 높은 등급)")
    void should_ignoreCutOrder() {
        List<GradeRules.Cut> shuffled = new ArrayList<>(rules.gradeCuts());
        Collections.reverse(shuffled);

        assertThat(GradeCalculator.gradeOf(85, shuffled)).isEqualTo(GradeCode.B);
    }

    // --- 시즌 구간 ---

    @Test
    @DisplayName("BR-03: 시즌 시작 정각(6월 1일 0시)은 이번 시즌에 들고, 직전(5월 31일 23:59:59.999999)은 안 든다")
    void should_includeStartInstant_andExcludeJustBefore() {
        GradeResult result = calc(List.of(
                line(1, SEASON_START, 1, 0, 0, 1_000_000, 0),
                line(2, SEASON_START.minusNanos(1_000), 1, 0, 0, 5_000_000, 0)));

        assertThat(result.ticketCount()).isEqualTo(1);
        assertThat(result.netAmount()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("BR-03: 끝 시각(다음 해 6월 1일 0시)은 포함하지 않고, 그 직전은 포함한다")
    void should_excludeEndInstant_andIncludeJustBefore() {
        GradeResult result = calc(List.of(
                line(1, SEASON_END, 1, 0, 0, 5_000_000, 0),
                line(2, SEASON_END.minusNanos(1_000), 1, 0, 0, 2_000_000, 0)));

        assertThat(result.ticketCount()).isEqualTo(1);
        assertThat(result.netAmount()).isEqualTo(2_000_000);
    }

    @Test
    @DisplayName("BR-03·NFR-10: 기간은 시작·끝을 받는다 — 36개월 기간이면 세 시즌의 구매가 모두 들어간다")
    void should_countWholePeriod_when_36Months() {
        List<PurchaseLine> lines = List.of(
                line(1, LocalDateTime.of(2023, 7, 1, 10, 0), 1, 0, 0, 1_000_000, 0),
                line(2, LocalDateTime.of(2024, 9, 1, 10, 0), 1, 0, 0, 1_000_000, 0),
                line(3, LocalDateTime.of(2025, 12, 1, 10, 0), 1, 0, 0, 1_000_000, 0),
                line(4, LocalDateTime.of(2026, 7, 1, 10, 0), 1, 0, 0, 9_000_000, 0));

        GradeResult result = GradeCalculator.calculate(lines, LocalDateTime.of(2023, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0), rules);

        assertThat(result.ticketCount()).isEqualTo(3);
        assertThat(result.netAmount()).isEqualTo(3_000_000);
    }

    // --- 구매 건 티켓 (BR-02) ---

    @Test
    @DisplayName("BR-02: 티켓은 구매 건마다 1개다 — 한 구매 건의 줄이 3개여도 티켓은 1, 다른 구매 건은 따로 센다")
    void should_countTicketPerPurchaseGroup() {
        GradeResult result = calc(List.of(buy(1, 100_000), buy(1, 200_000), buy(1, 300_000), buy(2, 50_000)));

        assertThat(result.ticketCount()).isEqualTo(2);
        assertThat(result.netAmount()).isEqualTo(650_000);
    }

    @Test
    @DisplayName("BR-02: 일부 반품은 티켓을 그대로 두고 금액·수량에서만 뺀다")
    void should_keepTicket_when_partialReturn() {
        GradeResult result = calc(List.of(
                line(1, IN_SEASON, 2, 1, 0, 400_000, 200_000),
                line(1, IN_SEASON, 1, 0, 0, 100_000, 0)));

        assertThat(result.ticketCount()).isEqualTo(1);
        assertThat(result.netAmount()).isEqualTo(300_000);
    }

    @Test
    @DisplayName("BR-02: 구매 건의 상품이 모두 반품되면 티켓에서 빠지고 금액은 0이다")
    void should_dropTicket_when_everythingReturned() {
        GradeResult result = calc(List.of(
                line(1, IN_SEASON, 1, 1, 0, 300_000, 300_000),
                line(1, IN_SEASON, 2, 2, 0, 200_000, 200_000)));

        assertThat(result.ticketCount()).isZero();
        assertThat(result.netAmount()).isZero();
        assertThat(result.grade()).isEqualTo(GradeCode.NORMAL);
    }

    @Test
    @DisplayName("BR-02·16: 기록 취소된 구매는 처음부터 없던 구매라 티켓·금액에서 빠진다")
    void should_ignoreVoidedPurchase() {
        GradeResult result = calc(List.of(
                line(1, IN_SEASON, 1, 1, 1, 500_000, 500_000),
                buy(2, 120_000)));

        assertThat(result.ticketCount()).isEqualTo(1);
        assertThat(result.netAmount()).isEqualTo(120_000);
    }

    @Test
    @DisplayName("BR-17: 교환 시나리오 — 1·2·3을 사고 1 환불, 2를 4·5로 교환, 3 환불 → 티켓은 처음 구매 건 1개, 금액은 4·5만")
    void should_keepOneTicketAndNetAmount_when_exchangeScenario() {
        GradeResult result = calc(List.of(
                line(100, IN_SEASON, 1, 1, 0, 100_000, 100_000), // 1 환불
                line(100, IN_SEASON, 1, 1, 0, 200_000, 200_000), // 2 교환으로 나감
                line(100, IN_SEASON, 1, 1, 0, 300_000, 300_000), // 3 환불
                line(100, IN_SEASON, 1, 0, 0, 250_000, 0),       // 4 (교환으로 받음, 처음 판매의 구매 건)
                line(100, IN_SEASON, 1, 0, 0, 200_000, 0)));     // 5

        assertThat(result.ticketCount()).isEqualTo(1);
        assertThat(result.netAmount()).isEqualTo(450_000);
    }

    @Test
    @DisplayName("BR-03·17: 지난 시즌 판매를 이번 시즌에 교환해도 새 상품의 실적 시각은 지난 시즌이라 이번 시즌 실적에 안 든다")
    void should_notCountExchangeOfPastSeasonInThisSeason() {
        LocalDateTime pastSeason = LocalDateTime.of(2026, 5, 20, 10, 0);

        GradeResult result = calc(List.of(
                line(7, pastSeason, 1, 1, 0, 100_000, 100_000),
                line(7, pastSeason, 1, 0, 0, 130_000, 0))); // 교환으로 받은 상품: countedAt = 처음 판매 시각

        assertThat(result.ticketCount()).isZero();
        assertThat(result.netAmount()).isZero();
    }

    @Test
    @DisplayName("구매가 없으면 티켓 0, 금액 0, 점수 0, 일반 등급이다")
    void should_returnNormal_when_noPurchases() {
        GradeResult result = calc(List.of());

        assertThat(result).isEqualTo(new GradeResult(0, 0, 0, 0, 0, GradeCode.NORMAL));
    }

    // --- 데모 기준값 예시 고객 (BR-04 표) ---

    @Test
    @DisplayName("BR-04: 예시 고객 1 — 100만원 1건 → 금액 10 + 티켓 10 = 20점, D")
    void should_beD_when_oneMillionOneTicket() {
        GradeResult result = calc(List.of(buy(1, 1_000_000)));

        assertThat(result).isEqualTo(new GradeResult(1, 1_000_000, 10, 10, 20, GradeCode.D));
    }

    @Test
    @DisplayName("BR-04: 예시 고객 2 — 250만원 3건 → 금액 20 + 티켓 30 = 50점, C")
    void should_beC_when_2_5MillionThreeTickets() {
        GradeResult result = calc(List.of(buy(1, 1_000_000), buy(2, 1_000_000), buy(3, 500_000)));

        assertThat(result).isEqualTo(new GradeResult(3, 2_500_000, 30, 20, 50, GradeCode.C));
    }

    @Test
    @DisplayName("BR-04: 예시 고객 3 — 330만원 4건 → 금액 40 + 티켓 40 = 80점, B")
    void should_beB_when_3_3MillionFourTickets() {
        GradeResult result = calc(List.of(buy(1, 1_000_000), buy(2, 1_000_000), buy(3, 1_000_000), buy(4, 300_000)));

        assertThat(result).isEqualTo(new GradeResult(4, 3_300_000, 40, 40, 80, GradeCode.B));
    }

    @Test
    @DisplayName("BR-04: 예시 고객 4 — 400만원 5건 → 금액 60 + 티켓 40 = 100점, A (A는 정확히 100점부터)")
    void should_beA_when_fourMillionFiveTickets() {
        GradeResult result = calc(List.of(buy(1, 800_000), buy(2, 800_000), buy(3, 800_000), buy(4, 800_000),
                buy(5, 800_000)));

        assertThat(result).isEqualTo(new GradeResult(5, 4_000_000, 40, 60, 100, GradeCode.A));
    }

    @Test
    @DisplayName("BR-04: 예시 고객 5 — 금액은 많아도 티켓이 1건이면 60 + 10 = 70점, B (A가 되려면 100점)")
    void should_beB_when_bigAmountSingleTicket() {
        GradeResult result = calc(List.of(buy(1, 5_000_000)));

        assertThat(result).isEqualTo(new GradeResult(1, 5_000_000, 10, 60, 70, GradeCode.B));
    }

    @Test
    @DisplayName("NFR-10: 기준은 설정값이다 — 다른 기준(엑셀 툴식 36개월 구간)을 넣으면 그 기준대로 계산한다")
    void should_followGivenRules_notHardcodedValues() {
        GradeRules excelStyle = new GradeRules(
                List.of(new GradeRules.Band(0, 0), new GradeRules.Band(3_000_000, 10), new GradeRules.Band(6_000_000, 20)),
                List.of(new GradeRules.Band(0, 0), new GradeRules.Band(2, 10), new GradeRules.Band(5, 20)),
                List.of(new GradeRules.Cut(GradeCode.NORMAL, 0), new GradeRules.Cut(GradeCode.D, 10),
                        new GradeRules.Cut(GradeCode.C, 25)));
        List<PurchaseLine> lines = List.of(buy(1, 3_000_000), buy(2, 3_000_000), buy(3, 100_000));

        GradeResult result = GradeCalculator.calculate(lines, SEASON_START, SEASON_END, excelStyle);

        assertThat(result).isEqualTo(new GradeResult(3, 6_100_000, 10, 20, 30, GradeCode.C));
    }
}

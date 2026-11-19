package com.crmpos.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.type.ItemType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 반품 환불 금액과 판매 상태 (BR-11, SAL-03). 나눠 반품해도 환불 합계가 결제 금액과 같다. */
class SaleReturnRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 14, 0);

    private static SaleLine line(int qty, long amount) {
        return SaleLine.of(null, null, "양말", null, ItemType.ACC, null, "FREE", null, qty, amount, null, null);
    }

    private static Sale sale(SaleLine... lines) {
        return Sale.record(1L, "GN01-20261119-0001", "k", "h", 17L, 3L, null, null, NOW, NOW, List.of(lines));
    }

    @Test
    @DisplayName("3개 100원을 1개씩 반품하면 33원·33원·34원이고 합계는 100원이다")
    void should_refund33_33_34_when_returnOneByOne() {
        SaleLine line = line(3, 100);

        long first = line.processReturn(1);
        long second = line.processReturn(1);
        long third = line.processReturn(1);

        assertThat(first).isEqualTo(33);
        assertThat(second).isEqualTo(33);
        assertThat(third).isEqualTo(34);
        assertThat(first + second + third).isEqualTo(100);
        assertThat(line.getReturnedQty()).isEqualTo(3);
        assertThat(line.getReturnedAmount()).isEqualTo(100);
    }

    @ParameterizedTest(name = "{0}개 {1}원에서 {2}개를 한 번에 반품하면 {3}원")
    @CsvSource({
            "3, 100, 1, 33",
            "3, 100, 2, 66",
            "3, 101, 2, 67",
            "4, 99000, 1, 24750",
            "4, 99000, 3, 74250",
            "2, 774060, 1, 387030",
            "3, 0, 1, 0",
    })
    @DisplayName("일부만 반품하면 결제 금액 × 반품 수량 ÷ 판매 수량의 원 미만 버림이다")
    void should_refundProportionally_when_partialQty(int qty, long amount, int returnQty, long expected) {
        assertThat(line(qty, amount).processReturn(returnQty)).isEqualTo(expected);
    }

    @Test
    @DisplayName("전부 한 번에 반품하면 결제 금액 전체를 돌려받는다 (나눠 떨어지지 않아도)")
    void should_refundAll_when_returnAllAtOnce() {
        assertThat(line(3, 100).processReturn(3)).isEqualTo(100);
        assertThat(line(7, 1000001).processReturn(7)).isEqualTo(1000001);
    }

    @Test
    @DisplayName("2개를 반품한 뒤 마지막 1개는 남은 금액 전부다")
    void should_refundRemainder_forLastQty() {
        SaleLine line = line(3, 101);

        long firstTwo = line.processReturn(2);
        long last = line.processReturn(1);

        assertThat(firstTwo).isEqualTo(67);
        assertThat(last).isEqualTo(34);
        assertThat(firstTwo + last).isEqualTo(101);
    }

    @Test
    @DisplayName("남은 수량보다 많이 반품하면 거절하고 아무것도 바뀌지 않는다")
    void should_reject_when_qtyExceedsRemaining() {
        SaleLine line = line(3, 100);
        line.processReturn(2);

        assertThatThrownBy(() -> line.processReturn(2)).isInstanceOf(IllegalArgumentException.class);

        assertThat(line.getReturnedQty()).isEqualTo(2);
        assertThat(line.getReturnedAmount()).isEqualTo(66);
        assertThat(line.remainingQty()).isEqualTo(1);
    }

    @Test
    @DisplayName("수량이 1보다 작으면 거절한다")
    void should_reject_when_qtyNotPositive() {
        assertThatThrownBy(() -> line(3, 100).processReturn(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line(3, 100).processReturn(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("큰 금액·수량에서도 넘치지 않는다 (1조 원 × 9999개)")
    void should_notOverflow_withLargeNumbers() {
        SaleLine line = line(9999, 1_000_000_000_000L);

        long refund = line.processReturn(5000);

        assertThat(refund).isEqualTo(1_000_000_000_000L * 5000 / 9999);
    }

    @Test
    @DisplayName("일부만 반품하면 PARTIAL_RETURNED, 모든 라인을 다 반품하면 RETURNED")
    void should_updateSaleStatus() {
        SaleLine a = line(2, 2000);
        SaleLine b = line(1, 500);
        Sale sale = sale(a, b);

        a.processReturn(1);
        sale.refreshReturnStatus();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.PARTIAL_RETURNED);

        a.processReturn(1);
        sale.refreshReturnStatus();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.PARTIAL_RETURNED);

        b.processReturn(1);
        sale.refreshReturnStatus();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.RETURNED);
    }

    @Test
    @DisplayName("반품 행은 RETURN 종류이고 금액은 라인 환불의 합이다")
    void should_buildReturnWithLines() {
        SaleLine a = line(3, 100);
        SaleLine b = line(1, 500);
        Sale sale = sale(a, b);

        SaleReturn ret = SaleReturn.recordReturn(sale, "key", "h".repeat(64), 17L, 3L, NOW,
                List.of(SaleReturn.Item.of(a, 1), SaleReturn.Item.of(b, 1)));

        assertThat(ret.getKind()).isEqualTo(ReturnKind.RETURN);
        assertThat(ret.getAmount()).isEqualTo(33 + 500);
        assertThat(ret.getLines()).hasSize(2);
        assertThat(ret.getLines()).extracting(SaleReturnLine::getAmount).containsExactly(33L, 500L);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.PARTIAL_RETURNED);
        assertThat(ret.getNewSale()).isNull();
    }

    @Test
    @DisplayName("기록 취소는 남은 라인을 모두 돌리고 판매를 VOIDED로 바꾼다 (VOID 종류, 환불은 남은 금액 전부)")
    void should_voidRemainingLines() {
        SaleLine a = line(3, 100);
        SaleLine b = line(1, 500);
        Sale sale = sale(a, b);
        a.processReturn(1);

        SaleReturn ret = SaleReturn.recordVoid(sale, "key", "h".repeat(64), 17L, 3L, NOW);

        assertThat(ret.getKind()).isEqualTo(ReturnKind.VOID);
        assertThat(ret.getAmount()).isEqualTo(67 + 500);
        assertThat(ret.getLines()).extracting(SaleReturnLine::getQty).containsExactly(2, 1);
        assertThat(a.remainingQty()).isZero();
        assertThat(a.getReturnedAmount()).isEqualTo(100);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.VOIDED);
    }

    @Test
    @DisplayName("이미 전부 반품해 남은 라인이 없으면 기록 취소를 만들 수 없다")
    void should_notVoid_whenNothingRemains() {
        SaleLine a = line(1, 500);
        Sale sale = sale(a);
        a.processReturn(1);

        assertThatThrownBy(() -> SaleReturn.recordVoid(sale, "key", "h".repeat(64), 17L, 3L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

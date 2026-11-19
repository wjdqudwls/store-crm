package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerPurchaseTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 19, 10, 0);

    private CustomerPurchase purchase(int qty) {
        return CustomerPurchase.record(null, 100L, 1L, 1L, T, 100L, T, Target.WOMEN, ItemType.OUTER, qty, 200_000L);
    }

    @Test
    @DisplayName("반품하면 나간 수량과 금액이 쌓이고 기록 취소분은 0이다")
    void should_addReturnedQty_when_returned() {
        CustomerPurchase p = purchase(2);

        p.applyReturn(1, 100_000L, false);

        assertThat(p.getReturnedQty()).isEqualTo(1);
        assertThat(p.getReturnedAmount()).isEqualTo(100_000L);
        assertThat(p.getVoidedQty()).isZero();
        assertThat(p.effectiveQty()).isEqualTo(2);
    }

    @Test
    @DisplayName("기록 취소하면 나간 수량과 기록 취소 수량이 함께 쌓여 없던 구매가 된다")
    void should_addVoidedQty_when_voided() {
        CustomerPurchase p = purchase(2);

        p.applyReturn(2, 200_000L, true);

        assertThat(p.getReturnedQty()).isEqualTo(2);
        assertThat(p.getVoidedQty()).isEqualTo(2);
        assertThat(p.effectiveQty()).isZero();
    }

    @Test
    @DisplayName("일부 반품 뒤 나머지를 기록 취소해도 각각 담긴다")
    void should_keepBoth_when_returnedThenVoided() {
        CustomerPurchase p = purchase(3);

        p.applyReturn(1, 10L, false);
        p.applyReturn(2, 20L, true);

        assertThat(p.getReturnedQty()).isEqualTo(3);
        assertThat(p.getVoidedQty()).isEqualTo(2);
        assertThat(p.getReturnedAmount()).isEqualTo(30L);
    }

    @Test
    @DisplayName("남은 수량보다 많이 나가거나 0 이하면 거부한다")
    void should_reject_when_qtyOutOfRange() {
        CustomerPurchase p = purchase(1);
        p.applyReturn(1, 1L, false);

        assertThatThrownBy(() -> p.applyReturn(1, 1L, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> purchase(1).applyReturn(0, 0L, false)).isInstanceOf(IllegalStateException.class);
    }
}

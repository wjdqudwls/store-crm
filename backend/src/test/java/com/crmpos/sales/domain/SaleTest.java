package com.crmpos.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 판매 기록의 업무 규칙 (SAL-01, BR-10·12). */
class SaleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 14, 3);

    private static SaleLine registered(int qty, long amount) {
        return SaleLine.of(5501L, "D2110", "울 블렌드 롱코트", Target.WOMEN, ItemType.OUTER, "차콜", "90", 798000L,
                qty, amount, null, null);
    }

    private static SaleLine direct(int qty, long amount) {
        return SaleLine.of(null, null, "캐시미어 머플러", null, ItemType.ACC, "오트밀", "FREE", null, qty, amount, 31L, "남편");
    }

    private static Sale sale(SaleLine... lines) {
        return Sale.record(1L, "GN01-20261015-0012", "6b0f6c1e-2f7a-4f3c-9a51-1d2c3e4f5a6b", "h".repeat(64), 17L, 3L,
                1024L, GradeCode.B, NOW, NOW.plusMinutes(2), List.of(lines));
    }

    @Test
    @DisplayName("합계 금액과 수량은 라인에서 더하고, 결제 금액은 받은 값 그대로다")
    void should_sumLines_withoutRecalculatingAmount() {
        Sale sale = sale(registered(1, 774060), direct(1, 217800));

        assertThat(sale.getTotalAmount()).isEqualTo(991860);
        assertThat(sale.getTotalQty()).isEqualTo(2);
        assertThat(sale.getLines()).extracting(SaleLine::getLineAmount).containsExactly(774060L, 217800L);
    }

    @Test
    @DisplayName("일반 판매로 기록되고 판매 시각과 기록 시각이 따로 저장된다")
    void should_recordNormalSale() {
        Sale sale = sale(registered(1, 774060));

        assertThat(sale.getKind()).isEqualTo(SaleKind.NORMAL);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getSoldAt()).isEqualTo(NOW);
        assertThat(sale.getCreatedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(sale.getAppliedGrade()).isEqualTo(GradeCode.B);
        assertThat(sale.getOriginSale()).isNull();
    }

    @Test
    @DisplayName("처음 판매는 자기 자신이다")
    void should_pointRootToSelf() {
        Sale sale = sale(registered(1, 1000));

        sale.assignRootToSelf();

        assertThat(sale.getRootSale()).isSameAs(sale);
    }

    @Test
    @DisplayName("결제 금액이 0원이어도 기록할 수 있다 (증정·전액 포인트)")
    void should_allowZeroAmount() {
        assertThat(sale(registered(1, 0)).getTotalAmount()).isZero();
    }

    @Test
    @DisplayName("수량이 1보다 작거나 금액이 음수이면 거절한다")
    void should_rejectInvalidLine() {
        assertThatThrownBy(() -> registered(0, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registered(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("라인이 없으면 판매를 만들 수 없다")
    void should_rejectEmptyLines() {
        assertThatThrownBy(() -> Sale.record(1L, "R", "k", "h", 17L, 3L, null, null, NOW, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비회원 판매는 고객 ID와 적용 등급이 없다")
    void should_allowGuestSale() {
        Sale sale = Sale.record(1L, "R", "k", "h", 17L, 3L, null, null, NOW, NOW, List.of(registered(1, 100)));

        assertThat(sale.getCustomerId()).isNull();
        assertThat(sale.getAppliedGrade()).isNull();
    }

    @Test
    @DisplayName("직접 입력 라인은 상품 ID·정가가 없고 착용자 호칭이 복사된다")
    void should_keepDirectLineSnapshot() {
        SaleLine line = direct(1, 217800);

        assertThat(line.getSkuId()).isNull();
        assertThat(line.getListPrice()).isNull();
        assertThat(line.getWearerLabel()).isEqualTo("남편");
        assertThat(line.getReturnedQty()).isZero();
    }

    @Test
    @DisplayName("영수증 번호는 매장코드-날짜-순번 4자리이고, 순번이 커져도 앞이 0으로 채워진다")
    void should_formatReceiptNumber() {
        LocalDate day = LocalDate.of(2026, 10, 15);

        assertThat(ReceiptNumber.format("GN01", day, 12)).isEqualTo("GN01-20261015-0012");
        assertThat(ReceiptNumber.format("GN01", day, 1)).isEqualTo("GN01-20261015-0001");
        assertThat(ReceiptNumber.format("GN01", day, 10000)).isEqualTo("GN01-20261015-10000");
    }
}

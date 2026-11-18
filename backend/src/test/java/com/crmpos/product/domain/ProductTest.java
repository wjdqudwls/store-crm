package com.crmpos.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상품과 SKU 만들기 (PRD-01): 색상 × 사이즈가 판매 단위가 된다. */
class ProductTest {

    private static Product coat() {
        return Product.create("D2110", "울 블렌드 롱코트", ItemType.OUTER, Target.WOMEN, 798000);
    }

    private static List<String> keys(Product product) {
        return product.getSkus().stream().map(s -> s.getColor() + "/" + s.getSize()).sorted().toList();
    }

    @Test
    @DisplayName("색상 2개 × 사이즈 3개이면 SKU가 6개 생긴다")
    void should_createSixSkus_when_twoColorsThreeSizes() {
        Product product = coat();

        product.replaceSkus(List.of("차콜", "블랙"), List.of("90", "95", "100"));

        assertThat(product.getSkus()).hasSize(6);
        assertThat(keys(product)).contains("차콜/90", "블랙/100");
    }

    @Test
    @DisplayName("색상이 없으면 색상 칸이 빈 문자열인 SKU가 사이즈마다 생긴다")
    void should_useEmptyColor_when_noColors() {
        Product product = coat();

        product.replaceSkus(List.of(), List.of("90", "95"));

        assertThat(keys(product)).containsExactly("/90", "/95");
    }

    @Test
    @DisplayName("다시 고르면 이미 있는 조합은 그대로 두고, 새 조합은 더하고, 빠진 조합은 지운다")
    void should_keepExistingAddNewRemoveMissing_when_replaceSkus() {
        Product product = coat();
        product.replaceSkus(List.of("차콜"), List.of("90", "95"));
        ProductSku kept = product.getSkus().stream().filter(s -> s.getSize().equals("90")).findFirst().orElseThrow();

        product.replaceSkus(List.of("차콜", "블랙"), List.of("90", "100"));

        assertThat(keys(product)).containsExactlyInAnyOrder("차콜/90", "차콜/100", "블랙/90", "블랙/100");
        assertThat(product.getSkus()).contains(kept);
    }

    @Test
    @DisplayName("같은 색상·사이즈를 두 번 적어도 SKU는 하나다")
    void should_dedupe_when_repeatedValues() {
        Product product = coat();

        product.replaceSkus(List.of("차콜", "차콜"), List.of("90", "90"));

        assertThat(product.getSkus()).hasSize(1);
    }

    @Test
    @DisplayName("상품을 고치면 값이 바뀐다")
    void should_changeValues_when_change() {
        Product product = coat();

        product.change(null, "숏 코트", ItemType.OUTER, null, 500000);

        assertThat(product.getProductCode()).isNull();
        assertThat(product.getName()).isEqualTo("숏 코트");
        assertThat(product.getTarget()).isNull();
        assertThat(product.getListPrice()).isEqualTo(500000);
    }
}

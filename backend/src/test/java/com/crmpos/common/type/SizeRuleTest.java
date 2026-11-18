package com.crmpos.common.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 품목·대상별 사이즈 허용값 (02-erd.md 3.2, PRD-01). 경계는 허용값 목록의 처음과 끝, 그 바로 바깥이다. */
class SizeRuleTest {

    @ParameterizedTest(name = "{0} {1} {2} → {3}")
    @CsvSource({
            // 여성 아우터 85~105
            "OUTER, WOMEN, 85, true", "OUTER, WOMEN, 105, true", "OUTER, WOMEN, 80, false", "OUTER, WOMEN, 110, false",
            // 남성 아우터 95~115, 공용은 남성 목록
            "OUTER, MEN, 95, true", "OUTER, MEN, 115, true", "OUTER, MEN, 90, false", "OUTER, MEN, 120, false",
            "OUTER, UNISEX, 115, true", "OUTER, UNISEX, 85, false",
            // 여성 하의 24~32, 남성 하의 28~38
            "BOTTOM, WOMEN, 24, true", "BOTTOM, WOMEN, 32, true", "BOTTOM, WOMEN, 34, false", "BOTTOM, WOMEN, 22, false",
            "BOTTOM, MEN, 28, true", "BOTTOM, MEN, 38, true", "BOTTOM, MEN, 26, false", "BOTTOM, MEN, 40, false",
            // 신발 220~280, 5 단위
            "SHOES, WOMEN, 220, true", "SHOES, WOMEN, 280, true", "SHOES, WOMEN, 215, false",
            "SHOES, WOMEN, 285, false", "SHOES, WOMEN, 222, false",
            // 상의 XS~XXL, 액세서리 FREE
            "TOP, WOMEN, XS, true", "TOP, WOMEN, XXL, true", "TOP, WOMEN, XXXL, false", "TOP, WOMEN, 90, false",
            "ACC, MEN, FREE, true", "ACC, MEN, M, false",
            // 아동 의류 4·6·8·10, 아동 신발은 다루지 않는다
            "OUTER, KIDS, 4, true", "OUTER, KIDS, 10, true", "OUTER, KIDS, 5, false", "OUTER, KIDS, 12, false",
            "TOP, KIDS, 6, true", "TOP, KIDS, M, false", "BOTTOM, KIDS, 8, true",
            "SHOES, KIDS, 220, false", "ACC, KIDS, FREE, true"
    })
    @DisplayName("대상이 있는 상품은 그 대상의 허용값만 쓸 수 있다")
    void should_checkAllowedSize_when_targetGiven(ItemType item, Target target, String size, boolean expected) {
        assertThat(SizeRule.isAllowed(item, target, size)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} 대상 없음 {1} → {2}")
    @CsvSource({
            "OUTER, 85, true", "OUTER, 115, true", "OUTER, 4, true", "OUTER, 120, false",
            "BOTTOM, 24, true", "BOTTOM, 38, true", "BOTTOM, 10, true", "BOTTOM, 40, false",
            "TOP, M, true", "TOP, 6, true", "TOP, 90, false",
            "SHOES, 250, true", "SHOES, 4, false",
            "ACC, FREE, true"
    })
    @DisplayName("대상이 없으면 그 품목의 모든 허용값에서 고를 수 있다")
    void should_allowEveryValue_when_targetMissing(ItemType item, String size, boolean expected) {
        assertThat(SizeRule.isAllowed(item, null, size)).isEqualTo(expected);
    }

    @Test
    @DisplayName("신발은 220부터 280까지 5 단위 13개이다")
    void should_haveThirteenShoeSizes() {
        List<String> shoes = SizeRule.allowed(ItemType.SHOES, null);

        assertThat(shoes).hasSize(13).startsWith("220", "225").endsWith("280");
    }

    @Test
    @DisplayName("대상 없는 아우터는 여성·남성·아동 목록을 겹치지 않게 합친다")
    void should_mergeLists_when_outerWithoutTarget() {
        assertThat(SizeRule.allowed(ItemType.OUTER, null))
                .containsExactly("85", "90", "95", "100", "105", "110", "115", "4", "6", "8", "10");
    }

    @Test
    @DisplayName("사이즈 목록은 값이 null이거나 비어 있으면 허용하지 않는다")
    void should_reject_when_sizeNullOrBlank() {
        assertThat(SizeRule.isAllowed(ItemType.TOP, null, null)).isFalse();
        assertThat(SizeRule.isAllowed(ItemType.TOP, null, " ")).isFalse();
    }

    @Test
    @DisplayName("공개 사이즈 목록은 품목·대상 묶음이고 대상과 관계없는 품목은 target이 null이다")
    void should_listGroups_when_publicSizes() {
        List<SizeRule.SizeGroup> groups = SizeRule.groups();

        assertThat(groups).contains(
                new SizeRule.SizeGroup(ItemType.OUTER, Target.WOMEN, List.of("85", "90", "95", "100", "105")),
                new SizeRule.SizeGroup(ItemType.OUTER, Target.KIDS, List.of("4", "6", "8", "10")),
                new SizeRule.SizeGroup(ItemType.TOP, null, List.of("XS", "S", "M", "L", "XL", "XXL")),
                new SizeRule.SizeGroup(ItemType.ACC, null, List.of("FREE")));
        assertThat(groups).noneMatch(g -> g.target() == Target.UNISEX);
        assertThat(groups).noneMatch(g -> g.itemType() == ItemType.SHOES && g.target() == Target.KIDS);
    }
}

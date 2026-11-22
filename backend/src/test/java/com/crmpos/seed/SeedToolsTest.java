package com.crmpos.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.type.Target;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** seed 도구의 작은 규칙만 확인한다. 생성기·측정기 전체는 서버를 띄워야 해서 손으로 돌린다 (docs/performance.md). */
class SeedToolsTest {

    @Test
    @DisplayName("더미 상품의 사이즈는 모두 사이즈 규칙의 허용값이다")
    void should_useAllowedSizes_when_buildingDummyProducts() {
        for (Object[] kind : DummyDataGenerator.PRODUCT_KINDS) {
            ItemType itemType = ItemType.valueOf((String) kind[0]);
            Target target = Target.valueOf((String) kind[1]);

            List<String> sizes = DummyDataGenerator.sizesFor(itemType.name(), target.name());

            assertThat(sizes).as("%s %s", itemType, target).isNotEmpty()
                    .allSatisfy(size -> assertThat(SizeRule.isAllowed(itemType, target, size)).isTrue());
        }
    }

    @Test
    @DisplayName("백분위수는 가까운 순위로 고른다")
    void should_pickNearestRank_when_percentile() {
        List<Long> sorted = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();

        assertThat(PerformanceMeasurer.pick(sorted, 50)).isEqualTo(50);
        assertThat(PerformanceMeasurer.pick(sorted, 95)).isEqualTo(95);
        assertThat(PerformanceMeasurer.pick(List.of(7L), 95)).isEqualTo(7);
    }
}

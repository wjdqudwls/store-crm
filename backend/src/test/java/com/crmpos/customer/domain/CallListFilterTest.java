package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 콜리스트 기본값과 등급 비교 (CAL-01, BR-09, BR-04, NFR-09). */
class CallListFilterTest {

    private static final CallListFilter.Request NOTHING =
            new CallListFilter.Request(null, null, null, null, null, null, null, null);

    private static Map<GradeCode, Integer> demoCuts() {
        Map<GradeCode, Integer> cuts = new EnumMap<>(GradeCode.class);
        cuts.put(GradeCode.NORMAL, 0);
        cuts.put(GradeCode.D, 20);
        cuts.put(GradeCode.C, 40);
        cuts.put(GradeCode.B, 70);
        cuts.put(GradeCode.A, 100);
        return cuts;
    }

    @Test
    @DisplayName("BR-09: 직원 기본값은 내 담당·등급 D 이상·90일 이상 미방문·홍보 가능 조건 없음")
    void should_applyEmployeeDefaults() {
        CallListFilter f = CallListFilter.resolve(StaffRole.SENIOR, 17L, NOTHING);

        assertThat(f.scope()).isEqualTo(CallListScope.MINE);
        assertThat(f.staffId()).isEqualTo(17L);
        assertThat(f.minGrade()).isEqualTo(GradeCode.D);
        assertThat(f.inactiveDays()).isEqualTo(90);
        assertThat(f.promoOnly()).isFalse();
    }

    @Test
    @DisplayName("BR-09: 매장 계정 기본값은 이 매장 고객 전체·홍보 연락 가능만이고 등급·방문 조건은 없다")
    void should_applyStoreDefaults() {
        CallListFilter f = CallListFilter.resolve(StaffRole.STORE, 3L, NOTHING);

        assertThat(f.scope()).isEqualTo(CallListScope.STORE);
        assertThat(f.staffId()).isNull();
        assertThat(f.promoOnly()).isTrue();
        assertThat(f.minGrade()).isNull();
        assertThat(f.inactiveDays()).isNull();
    }

    @Test
    @DisplayName("BR-09: 본사는 매장 계정과 같은 기본값이다 (매장은 storeId로 고른다)")
    void should_applyHqDefaults_sameAsStore() {
        CallListFilter f = CallListFilter.resolve(StaffRole.HQ, 1L, NOTHING);

        assertThat(f.scope()).isEqualTo(CallListScope.STORE);
        assertThat(f.promoOnly()).isTrue();
        assertThat(f.minGrade()).isNull();
    }

    @Test
    @DisplayName("요청이 준 값은 기본값보다 우선하고, 기본값은 명시해서 끈다 (NORMAL·0일·false)")
    void should_preferRequestedValues() {
        CallListFilter.Request req = new CallListFilter.Request(CallListScope.STORE, 23L, false, GradeCode.NORMAL, 0,
                Target.WOMEN, ItemType.OUTER, MemberRelation.CHILD);

        CallListFilter f = CallListFilter.resolve(StaffRole.JUNIOR, 17L, req);

        assertThat(f.scope()).isEqualTo(CallListScope.STORE);
        assertThat(f.staffId()).isEqualTo(23L);
        assertThat(f.promoOnly()).isFalse();
        assertThat(f.minGrade()).isEqualTo(GradeCode.NORMAL);
        assertThat(f.inactiveDays()).isZero();
        assertThat(f.target()).isEqualTo(Target.WOMEN);
        assertThat(f.itemType()).isEqualTo(ItemType.OUTER);
        assertThat(f.memberRelation()).isEqualTo(MemberRelation.CHILD);
    }

    @Test
    @DisplayName("직원이 매장 범위(STORE)를 고르면 내 담당 제한은 붙지 않는다 (staffId를 따로 주지 않으면)")
    void should_notRestrictToSelf_whenEmployeePicksStoreScope() {
        CallListFilter f = CallListFilter.resolve(StaffRole.STAFF, 17L,
                new CallListFilter.Request(CallListScope.STORE, null, null, null, null, null, null, null));

        assertThat(f.staffId()).isNull();
    }

    @Test
    @DisplayName("BR-04: 최소 등급 이상은 등급 컷 점수 순서로 정한다 (B 이상 = B·A, D 이상 = D·C·B·A)")
    void should_listGradesAtLeast_byCutScore() {
        assertThat(CallListFilter.gradesAtLeast(GradeCode.B, demoCuts())).containsExactlyInAnyOrder(GradeCode.B, GradeCode.A);
        assertThat(CallListFilter.gradesAtLeast(GradeCode.D, demoCuts()))
                .containsExactlyInAnyOrder(GradeCode.D, GradeCode.C, GradeCode.B, GradeCode.A);
        assertThat(CallListFilter.gradesAtLeast(GradeCode.NORMAL, demoCuts())).hasSize(5);
        assertThat(CallListFilter.gradesAtLeast(GradeCode.A, demoCuts())).containsExactly(GradeCode.A);
    }

    @Test
    @DisplayName("BR-04: 컷 점수를 바꾸면 순서도 따라 바뀐다 (본사가 정한 값이 기준)")
    void should_followCutScores_notEnumOrder() {
        Map<GradeCode, Integer> odd = demoCuts();
        odd.put(GradeCode.C, 80); // C 컷이 B보다 높아진 가상 기준

        assertThat(CallListFilter.gradesAtLeast(GradeCode.B, odd)).containsExactlyInAnyOrder(GradeCode.B, GradeCode.C, GradeCode.A);
    }

    @Test
    @DisplayName("최소 등급이 없으면 조건 없음(null), 컷 정보가 없으면 그 등급 하나만")
    void should_handleNoMinGradeOrNoCuts() {
        assertThat(CallListFilter.gradesAtLeast(null, demoCuts())).isNull();
        assertThat(CallListFilter.gradesAtLeast(GradeCode.B, Map.of())).containsExactly(GradeCode.B);
    }
}

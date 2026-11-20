package com.crmpos.customer.domain;

import com.crmpos.common.type.GradeCode;
import java.util.List;

/** 테스트용 기준값: 01-requirements.md BR-04의 데모용 초기값 (실제 운영 값이 아니다). */
final class GradeTestRules {

    private GradeTestRules() {
    }

    static GradeRules demo() {
        return new GradeRules(
                List.of(new GradeRules.Band(0, 0), new GradeRules.Band(1_000_000, 10), new GradeRules.Band(2_000_000, 20),
                        new GradeRules.Band(3_000_000, 40), new GradeRules.Band(4_000_000, 60)),
                List.of(new GradeRules.Band(0, 0), new GradeRules.Band(1, 10), new GradeRules.Band(2, 20),
                        new GradeRules.Band(3, 30), new GradeRules.Band(4, 40)),
                List.of(new GradeRules.Cut(GradeCode.NORMAL, 0), new GradeRules.Cut(GradeCode.D, 20),
                        new GradeRules.Cut(GradeCode.C, 40), new GradeRules.Cut(GradeCode.B, 70),
                        new GradeRules.Cut(GradeCode.A, 100)));
    }
}

package com.crmpos.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.crmpos.common.type.GradeCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 등급 기준의 구간 한 줄. AMOUNT·TICKET은 점수, GRADE는 등급 컷과 할인율. */
@Entity
@Table(name = "grade_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private GradePolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeRuleKind kind;

    @Column(nullable = false)
    private long minValue;

    @Column
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GradeCode gradeCode;

    @Column
    private Integer discountRate;
}

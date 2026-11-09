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

import java.time.LocalDateTime;

import com.crmpos.common.type.GradeCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 등급 이력 (GRD-06). 등급이 바뀔 때, 그리고 정기 산정·임시 재계산 때는 등급이 그대로여도 고객마다 1행 (ADR-021). */
@Entity
@Table(name = "grade_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCode fromGrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCode toGrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeChangeReason reason;

    /** 점수를 계산한 시즌 (예: 25/26). */
    @Column(nullable = false, length = 10)
    private String season;

    /** 아래 세 값은 그때의 실적. "왜 이 등급인지"를 보여준다 (GRD-03). */
    @Column(nullable = false)
    private int ticketCount;

    @Column(nullable = false)
    private long netAmount;

    @Column(nullable = false)
    private int totalScore;

    @Column(nullable = false)
    private int policyVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

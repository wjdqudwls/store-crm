package com.crmpos.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 등급 기준. 고치지 않고 새 버전을 만든다 (GRD-04). */
@Entity
@Table(name = "grade_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int version;

    /** 시즌 길이 (12). 시작일은 6월 1일 고정. */
    @Column(nullable = false)
    private int periodMonths;

    /** 적용 시작일. 적용 중인 기준은 이 날이 오늘 이전인 것 중 가장 최근 버전이다 (ADR-027). */
    @Column(nullable = false)
    private LocalDate appliesFrom;

    @Column
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

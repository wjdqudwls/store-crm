package com.crmpos.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    @Column(nullable = false)
    private int periodMonths;

    @Column(nullable = false)
    private boolean active;

    @Column
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

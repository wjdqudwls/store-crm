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

/** 등급 변경 이력 (GRD-06). */
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

    @Column(nullable = false)
    private int policyVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

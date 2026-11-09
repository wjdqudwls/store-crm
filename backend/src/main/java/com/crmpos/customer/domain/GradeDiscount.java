package com.crmpos.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import com.crmpos.common.type.GradeCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 등급별 할인율 (안내용, BR-10). 등급 기준 버전과 따로 두고, 바꾸면 바로 적용한다 (ADR-027). */
@Entity
@Table(name = "grade_discount")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeDiscount {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GradeCode gradeCode;

    /** 할인율 %. */
    @Column(nullable = false)
    private int rate;

    /** 바꾼 본사 계정. 시드 데이터는 null. common 모듈의 ID (ADR-003). */
    @Column
    private Long updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

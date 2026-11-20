package com.crmpos.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 후속 연락 단계 설정. 브랜드 공통이고 MANAGER가 바꾼다 (BR-14, CAL-07). */
@Entity
@Table(name = "follow_up_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowUpRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 판매일로부터의 기간 (예: 2). 단위는 offsetUnit. */
    @Column(nullable = false)
    private int offsetAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpOffsetUnit offsetUnit;

    /** 홍보 연락 단계. 수신 미동의 고객에게는 "홍보 연락 불가"로 표시한다 (BR-08). */
    @Column(nullable = false)
    private boolean promotional;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static FollowUpRule create(String name, int offsetAmount, FollowUpOffsetUnit offsetUnit, boolean promotional,
            int sortOrder, boolean active, LocalDateTime now) {
        FollowUpRule rule = new FollowUpRule();
        rule.apply(name, offsetAmount, offsetUnit, promotional, sortOrder, active, now);
        return rule;
    }

    /** 설정을 고친다. 이미 만든 할 일은 복사본을 가지고 있어 그대로다 (BR-14). */
    public void apply(String name, int offsetAmount, FollowUpOffsetUnit offsetUnit, boolean promotional,
            int sortOrder, boolean active, LocalDateTime now) {
        this.name = name;
        this.offsetAmount = offsetAmount;
        this.offsetUnit = offsetUnit;
        this.promotional = promotional;
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
    }

    /** 지우지 않고 끈다 (이미 만든 할 일이 이 단계를 가리킨다). */
    public void disable(LocalDateTime now) {
        this.active = false;
        this.updatedAt = now;
    }
}

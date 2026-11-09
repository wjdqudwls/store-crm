package com.crmpos.common.domain;

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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "staff")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    /** 활성 MANAGER만 MAIN·TEMP, 그 밖에는 null (BR-15). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ManagerSlot managerSlot;

    /** 본사 계정은 null. 같은 common 모듈이라 연관관계로 둔다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(length = 20)
    private String staffCode;

    @Column(nullable = false)
    private boolean active;

    /** 이 시각보다 먼저 발급된 토큰은 거절한다 (ADR-025). */
    @Column(nullable = false)
    private LocalDateTime tokensValidAfter;
}

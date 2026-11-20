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

    /** 계정을 만든다. 만든 시각이 로그인 기준 시각이 된다 (ADR-025). */
    public static Staff create(String loginId, String passwordHash, String name, StaffRole role,
            ManagerSlot managerSlot, Store store, String staffCode, LocalDateTime now) {
        Staff staff = new Staff();
        staff.loginId = loginId;
        staff.passwordHash = passwordHash;
        staff.name = name;
        staff.role = role;
        staff.managerSlot = managerSlot;
        staff.store = store;
        staff.staffCode = staffCode;
        staff.active = true;
        staff.tokensValidAfter = now;
        return staff;
    }

    /** 비밀번호를 바꾼다. 이전에 발급한 모든 토큰이 끊기도록 로그인 기준 시각도 지금으로 올린다 (AUTH-04, ADR-025). */
    public void changePassword(String newPasswordHash, LocalDateTime now) {
        this.passwordHash = newPasswordHash;
        this.tokensValidAfter = now;
    }

    /** 모든 기기에서 로그아웃: 지금 이전에 발급한 토큰을 거절한다 (AUTH-03, ADR-025). */
    public void invalidateTokens(LocalDateTime now) {
        this.tokensValidAfter = now;
    }
}

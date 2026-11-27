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

import com.crmpos.common.type.Gender;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대표 고객 아래 가족 구성원. 실명 대신 호칭 (BR-13, ADR-009). */
@Entity
@Table(name = "customer_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRelation relation;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column
    private Short birthYear;

    @Column(length = 20)
    private String sizeTop;

    @Column(length = 20)
    private String sizeOuter;

    @Column(length = 20)
    private String sizeBottom;

    @Column(length = 20)
    private String sizeShoes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 구성원을 만든다 (CUS-05). 호칭만 받고 실명·전화번호는 없다 (BR-13). */
    public static CustomerMember create(Customer customer, String nickname, MemberRelation relation, Gender gender,
            Short birthYear, String sizeTop, String sizeOuter, String sizeBottom, String sizeShoes,
            LocalDateTime now) {
        CustomerMember member = new CustomerMember();
        member.customer = customer;
        member.createdAt = now;
        member.change(nickname, relation, gender, birthYear, sizeTop, sizeOuter, sizeBottom, sizeShoes);
        return member;
    }

    /** 전체를 새 값으로 바꾼다. 보내지 않은 선택 칸은 비운다. 과거 판매의 착용자 호칭은 복사본이라 그대로다. */
    public void change(String nickname, MemberRelation relation, Gender gender, Short birthYear, String sizeTop,
            String sizeOuter, String sizeBottom, String sizeShoes) {
        this.nickname = nickname;
        this.relation = relation;
        this.gender = gender;
        this.birthYear = birthYear;
        this.sizeTop = sizeTop;
        this.sizeOuter = sizeOuter;
        this.sizeBottom = sizeBottom;
        this.sizeShoes = sizeShoes;
    }
}

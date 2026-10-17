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
import java.util.List;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 고객. 식별 키는 id(고객번호), 전화번호는 중복 확인용 (BR-01). */
@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String nameEn;

    /** 전화번호 암호문 (NFR-04). */
    @Column(nullable = false)
    private byte[] phoneEnc;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String phoneHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 4)
    private String phoneLast4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nationality nationality;

    @Column
    private Short birthYear;

    /** MMDD */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 4)
    private String birthMd;

    @Column(length = 20)
    private String regionSido;

    @Column(length = 20)
    private String regionGu;

    /** 상세주소 암호문 (BR-08). */
    @Column
    private byte[] addressDetailEnc;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<ItemType> preferredItemTypes;

    @Column(length = 20)
    private String sizeTop;

    @Column(length = 20)
    private String sizeOuter;

    @Column(length = 20)
    private String sizeBottom;

    @Column(length = 20)
    private String sizeShoes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContactChannel contactChannel;

    /** 가입매장. 다른 모듈(common)의 ID (ADR-003). */
    @Column
    private Long homeStoreId;

    /** 담당 직원. 정할 수 없으면 null (REG-02·03). */
    @Column
    private Long staffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerSource source;

    @Column(nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(nullable = false)
    private LocalDateTime age14ConfirmedAt;

    @Column
    private LocalDateTime marketingSmsAgreedAt;

    @Column
    private LocalDateTime marketingKakaoAgreedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

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

    /**
     * 고객을 등록한다 (REG-01·06). 외국인은 영문 이름이 필수이고 한글 이름을 비우면 영문 이름을 이름 칸에도 넣는다.
     * 내국인은 이름이 필수이고 영문 이름을 쓰지 않는다. 개인정보 동의와 만 14세 확인이 없으면 만들 수 없다.
     * 마케팅 동의는 동의한 것만 시각을 남긴다.
     */
    public static Customer register(CustomerDraft draft, LocalDateTime now) {
        if (!draft.privacyAgreed() || !draft.age14Confirmed()) {
            throw new IllegalArgumentException("개인정보 수집·이용 동의와 만 14세 이상 확인이 필요합니다.");
        }
        Customer customer = new Customer();
        customer.applyProfile(draft);
        customer.homeStoreId = draft.homeStoreId();
        customer.source = draft.source();
        customer.privacyAgreedAt = now;
        customer.age14ConfirmedAt = now;
        customer.marketingSmsAgreedAt = draft.marketingSms() ? now : null;
        customer.marketingKakaoAgreedAt = draft.marketingKakao() ? now : null;
        customer.createdAt = now;
        return customer;
    }

    /**
     * 프로필을 고친다 (CUS-04). 등록과 같은 이름 규칙을 쓴다. 가입매장·등록 경로·동의 시각·가입일은 그대로 두고,
     * 마케팅 수신은 새로 동의할 때만 시각을 남기고 철회하면 지운다.
     */
    public void updateProfile(CustomerDraft draft, LocalDateTime now) {
        applyProfile(draft);
        this.marketingSmsAgreedAt = marketingTime(draft.marketingSms(), this.marketingSmsAgreedAt, now);
        this.marketingKakaoAgreedAt = marketingTime(draft.marketingKakao(), this.marketingKakaoAgreedAt, now);
    }

    private static LocalDateTime marketingTime(boolean agreed, LocalDateTime before, LocalDateTime now) {
        if (!agreed) {
            return null;
        }
        return before != null ? before : now;
    }

    /** 외국인은 영문 이름이 필수이고 한글 이름을 비우면 영문 이름을 이름 칸에도 넣는다 (REG-06). 내국인은 이름이 필수다. */
    private void applyProfile(CustomerDraft draft) {
        boolean foreign = draft.nationality() == Nationality.FOREIGN;
        String nameEn = blankToNull(draft.nameEn());
        String name = blankToNull(draft.name());
        if (foreign) {
            if (nameEn == null) {
                throw new IllegalArgumentException("외국인은 영문 이름이 필요합니다.");
            }
            if (name == null) {
                name = nameEn;
            }
        } else {
            if (name == null) {
                throw new IllegalArgumentException("이름이 필요합니다.");
            }
            if (nameEn != null) {
                throw new IllegalArgumentException("내국인은 영문 이름을 쓰지 않습니다.");
            }
        }
        this.name = name;
        this.nameEn = nameEn;
        this.phoneEnc = draft.phoneEnc();
        this.phoneHash = draft.phoneHash();
        this.phoneLast4 = draft.phoneLast4();
        this.nationality = draft.nationality();
        this.birthYear = draft.birthYear();
        this.birthMd = draft.birthMd();
        this.regionSido = draft.regionSido();
        this.regionGu = draft.regionGu();
        this.addressDetailEnc = draft.addressDetailEnc();
        this.gender = draft.gender();
        this.preferredItemTypes = draft.preferredItemTypes();
        this.sizeTop = draft.sizeTop();
        this.sizeOuter = draft.sizeOuter();
        this.sizeBottom = draft.sizeBottom();
        this.sizeShoes = draft.sizeShoes();
        this.contactChannel = draft.contactChannel();
        this.staffId = draft.staffId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

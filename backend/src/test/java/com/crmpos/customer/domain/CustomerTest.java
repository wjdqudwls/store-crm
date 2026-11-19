package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 고객 등록의 업무 규칙 (REG-01·06, BR-08). */
class CustomerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 30);

    private static CustomerDraft.Builder draft() {
        return CustomerDraft.builder()
                .name("김하늘").nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1, 2, 3}).phoneHash("a".repeat(64)).phoneLast4("4821")
                .privacyAgreed(true).age14Confirmed(true)
                .homeStoreId(3L).source(CustomerSource.STORE);
    }

    @Test
    @DisplayName("내국인 등록: 이름과 동의 시각이 저장되고 마케팅 동의는 안 했으면 시각이 없다")
    void should_registerDomestic() {
        Customer customer = Customer.register(draft().marketingSms(true).build(), NOW);

        assertThat(customer.getName()).isEqualTo("김하늘");
        assertThat(customer.getPrivacyAgreedAt()).isEqualTo(NOW);
        assertThat(customer.getAge14ConfirmedAt()).isEqualTo(NOW);
        assertThat(customer.getMarketingSmsAgreedAt()).isEqualTo(NOW);
        assertThat(customer.getMarketingKakaoAgreedAt()).isNull();
        assertThat(customer.getCreatedAt()).isEqualTo(NOW);
        assertThat(customer.getSource()).isEqualTo(CustomerSource.STORE);
    }

    @Test
    @DisplayName("외국인은 한글 이름을 비우면 영문 이름이 이름 칸에도 들어간다")
    void should_fillNameWithNameEn_when_foreignWithoutKoreanName() {
        Customer customer = Customer.register(draft().name(null).nationality(Nationality.FOREIGN)
                .nameEn("Emma Smith").build(), NOW);

        assertThat(customer.getName()).isEqualTo("Emma Smith");
        assertThat(customer.getNameEn()).isEqualTo("Emma Smith");
    }

    @Test
    @DisplayName("외국인이 한글 이름도 적으면 둘 다 그대로 저장한다")
    void should_keepBothNames_when_foreignWithKoreanName() {
        Customer customer = Customer.register(draft().name("에마").nationality(Nationality.FOREIGN)
                .nameEn("Emma Smith").build(), NOW);

        assertThat(customer.getName()).isEqualTo("에마");
        assertThat(customer.getNameEn()).isEqualTo("Emma Smith");
    }

    @Test
    @DisplayName("외국인인데 영문 이름이 없으면 거절한다")
    void should_reject_when_foreignWithoutNameEn() {
        assertThatThrownBy(() -> Customer.register(draft().nationality(Nationality.FOREIGN).build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("내국인인데 이름이 없거나 영문 이름이 있으면 거절한다")
    void should_reject_when_domesticNameRulesBroken() {
        assertThatThrownBy(() -> Customer.register(draft().name(" ").build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Customer.register(draft().nameEn("Kim").build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("개인정보 동의나 만 14세 확인이 없으면 거절한다")
    void should_reject_when_consentMissing() {
        assertThatThrownBy(() -> Customer.register(draft().privacyAgreed(false).build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Customer.register(draft().age14Confirmed(false).build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("선택 항목은 그대로 저장된다")
    void should_storeOptionalFields() {
        Customer customer = Customer.register(draft().gender(Gender.F).birthYear((short) 1991).birthMd("0314")
                .regionSido("서울").regionGu("강남구").preferredItemTypes(List.of(ItemType.OUTER))
                .sizeTop("M").sizeOuter("90").sizeBottom("26").sizeShoes("240")
                .contactChannel(ContactChannel.KAKAO).staffId(12L).build(), NOW);

        assertThat(customer.getGender()).isEqualTo(Gender.F);
        assertThat(customer.getBirthYear()).isEqualTo((short) 1991);
        assertThat(customer.getSizeOuter()).isEqualTo("90");
        assertThat(customer.getPreferredItemTypes()).containsExactly(ItemType.OUTER);
        assertThat(customer.getStaffId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("고객 요약은 NORMAL 등급과 0점·0원으로 시작한다")
    void should_startSummaryAtNormalZero() {
        CustomerSummary summary = CustomerSummary.initial(7L, 1, NOW);

        assertThat(summary.getCustomerId()).isEqualTo(7L);
        assertThat(summary.getCurrentGrade()).isEqualTo(GradeCode.NORMAL);
        assertThat(summary.getComputedGrade()).isEqualTo(GradeCode.NORMAL);
        assertThat(summary.getConfirmedGrade()).isEqualTo(GradeCode.NORMAL);
        assertThat(summary.getTotalScore()).isZero();
        assertThat(summary.getSeasonNetAmount()).isZero();
        assertThat(summary.getLastVisitAt()).isNull();
        assertThat(summary.getPolicyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("등록 초안의 toString은 전화번호·이름·주소를 보이지 않는다")
    void should_maskPersonalData_when_draftToString() {
        String text = draft().name("김하늘").regionSido("서울").build().toString();

        assertThat(text).doesNotContain("김하늘").doesNotContain("4821").doesNotContain("서울");
    }
}

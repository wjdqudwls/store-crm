package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 전화번호 정규화 (BR-01, ADR-019): 국내는 +82, 외국은 +와 숫자만, 숫자는 최대 15자리. */
class PhoneNumberTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "010-1234-5678, +821012345678",
            "01012345678, +821012345678",
            "'010 1234 5678', +821012345678",
            "(010)1234.5678, +821012345678",
            "02-123-4567, +82212 34567",
    })
    @DisplayName("국내 번호는 앞의 0을 +82로 바꾼다")
    void should_normalizeDomestic(String raw, String expected) {
        assertThat(PhoneNumber.normalize(raw, Nationality.DOMESTIC)).isEqualTo(expected.replace(" ", ""));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "+1 (415) 555-2671, +14155552671",
            "+81-90-1234-5678, +819012345678",
            "+82 10 1234 5678, +821012345678",
    })
    @DisplayName("+로 시작하면 + 와 숫자만 남긴다 (국내·외국 모두)")
    void should_keepPlusAndDigits(String raw, String expected) {
        assertThat(PhoneNumber.normalize(raw, Nationality.FOREIGN)).isEqualTo(expected);
        assertThat(PhoneNumber.normalize(raw, Nationality.DOMESTIC)).isEqualTo(expected);
    }

    @Test
    @DisplayName("숫자 15자리까지는 통과하고 16자리부터 거절한다")
    void should_limitTo15Digits() {
        assertThat(PhoneNumber.normalize("+123456789012345", Nationality.FOREIGN)).isEqualTo("+123456789012345");
        assertThat(PhoneNumber.normalize("+1234567890123456", Nationality.FOREIGN)).isNull();
    }

    @Test
    @DisplayName("숫자 8자리까지는 통과하고 7자리는 거절한다")
    void should_requireAtLeast8Digits() {
        assertThat(PhoneNumber.normalize("+12345678", Nationality.FOREIGN)).isEqualTo("+12345678");
        assertThat(PhoneNumber.normalize("+1234567", Nationality.FOREIGN)).isNull();
    }

    @Test
    @DisplayName("외국인은 국가번호(+)가 있어야 한다")
    void should_requirePlus_when_foreign() {
        assertThat(PhoneNumber.normalize("010-1234-5678", Nationality.FOREIGN)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "010-12a4-5678", "+", "+0123456789", "00123456789", "12345678", "010-1234-5678#1"})
    @DisplayName("비었거나 숫자가 아닌 글자가 섞였거나 국가번호가 0으로 시작하면 거절한다")
    void should_rejectInvalid(String raw) {
        assertThat(PhoneNumber.normalize(raw, Nationality.DOMESTIC)).isNull();
    }

    @Test
    @DisplayName("null이면 거절한다")
    void should_rejectNull() {
        assertThat(PhoneNumber.normalize(null, Nationality.DOMESTIC)).isNull();
    }

    @Test
    @DisplayName("뒷자리 4개와 마스킹 표시")
    void should_provideLast4AndMasked() {
        assertThat(PhoneNumber.last4("+821029434821")).isEqualTo("4821");
        assertThat(PhoneNumber.masked("4821")).isEqualTo("****-4821");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "+821029434821, 010-2943-4821",
            "+82101234567, 010-123-4567",
            "+82212345678, 02-1234-5678",
            "+8221234567, 02-123-4567",
            "+82311234567, 031-123-4567",
    })
    @DisplayName("한국 번호는 읽기 쉬운 하이픈 모양으로 보여준다")
    void should_displayKoreanNumber(String e164, String expected) {
        assertThat(PhoneNumber.display(e164)).isEqualTo(expected);
    }

    @Test
    @DisplayName("외국 번호는 +로 시작하는 저장 형식 그대로 보여준다")
    void should_displayForeignAsStored() {
        assertThat(PhoneNumber.display("+14155552671")).isEqualTo("+14155552671");
    }
}

package com.crmpos.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 개인정보 암호화 (NFR-04). 테스트용 키라서 값은 실제 키가 아니다. */
class AesGcmCipherTest {

    private static String key(int bytes, int fill) {
        byte[] raw = new byte[bytes];
        java.util.Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final AesGcmCipher cipher = new AesGcmCipher(key(32, 7));

    @Test
    @DisplayName("암호화한 뒤 복호화하면 원래 값이 나온다")
    void should_roundTrip_when_encryptThenDecrypt() {
        byte[] encrypted = cipher.encrypt("+821029434821");

        assertThat(cipher.decrypt(encrypted)).isEqualTo("+821029434821");
    }

    @Test
    @DisplayName("한글 상세주소도 왕복한다")
    void should_roundTripKorean_when_address() {
        assertThat(cipher.decrypt(cipher.encrypt("테헤란로 123, 4층 401호"))).isEqualTo("테헤란로 123, 4층 401호");
    }

    @Test
    @DisplayName("암호문에는 평문이 그대로 들어 있지 않다")
    void should_notContainPlaintext_when_encrypted() {
        byte[] encrypted = cipher.encrypt("+821029434821");

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("1029434821");
    }

    @Test
    @DisplayName("같은 값을 두 번 암호화하면 암호문이 서로 다르다 (매번 새 IV)")
    void should_differEachTime_when_sameValueEncrypted() {
        assertThat(cipher.encrypt("+821029434821")).isNotEqualTo(cipher.encrypt("+821029434821"));
    }

    @Test
    @DisplayName("암호문이 바뀌면 복호화가 실패한다 (GCM 검증)")
    void should_fail_when_ciphertextTampered() {
        byte[] encrypted = cipher.encrypt("+821029434821");
        encrypted[encrypted.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void should_fail_when_wrongKey() {
        byte[] encrypted = cipher.encrypt("+821029434821");
        AesGcmCipher other = new AesGcmCipher(key(32, 9));

        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 길이가 32바이트가 아니면 시작할 때 실패한다")
    void should_failFast_when_keyWrongLength() {
        assertThatThrownBy(() -> new AesGcmCipher(key(16, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AesGcmCipher("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AesGcmCipher("not base64 !!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("에러 메시지에 키나 평문이 들어 있지 않다")
    void should_notLeakSecrets_when_decryptFails() {
        byte[] encrypted = cipher.encrypt("+821029434821");
        encrypted[5] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .hasMessageNotContaining("1029434821")
                .hasMessageNotContaining(key(32, 7));
    }
}

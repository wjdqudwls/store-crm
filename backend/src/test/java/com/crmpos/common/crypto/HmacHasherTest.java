package com.crmpos.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacHasherTest {

    private static String key(int bytes, int fill) {
        byte[] raw = new byte[bytes];
        Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    @DisplayName("같은 값은 같은 해시, 다른 값은 다른 해시이고 16진수 64자다")
    void should_beDeterministicHex64() {
        HmacHasher hasher = new HmacHasher(key(32, 3));

        String hash = hasher.hash("+821029434821");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hasher.hash("+821029434821")).isEqualTo(hash);
        assertThat(hasher.hash("+821029434822")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("키가 다르면 같은 값도 해시가 다르다 (키를 섞은 해시)")
    void should_differ_when_keyDiffers() {
        assertThat(new HmacHasher(key(32, 3)).hash("+821029434821"))
                .isNotEqualTo(new HmacHasher(key(32, 4)).hash("+821029434821"));
    }

    @Test
    @DisplayName("키가 32바이트보다 짧으면 시작할 때 실패한다")
    void should_failFast_when_keyTooShort() {
        assertThatThrownBy(() -> new HmacHasher(key(16, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacHasher("")).isInstanceOf(IllegalArgumentException.class);
    }
}

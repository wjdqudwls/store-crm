package com.crmpos.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 전화번호·상세주소 암호화 (NFR-04). AES-256-GCM, 매번 새 12바이트 IV를 암호문 앞에 붙인다.
 * 서비스 계층에서 호출하므로 목록 조회가 복호화 없이 {@code phone_last4}만 읽는다.
 */
@Component
public class AesGcmCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipher(@Value("${crm.crypto.key}") String base64Key) {
        this.key = new SecretKeySpec(Keys.decode(base64Key, "CRYPTO_KEY", 32, true), "AES");
    }

    public byte[] encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.");
        }
    }

    public String decrypt(byte[] data) {
        try {
            if (data == null || data.length <= IV_BYTES) {
                throw new IllegalStateException("복호화에 실패했습니다.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 원인 예외에 암호문이 들어갈 수 있어 붙이지 않는다
            throw new IllegalStateException("복호화에 실패했습니다.");
        }
    }
}

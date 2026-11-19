package com.crmpos.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 전화번호 중복 확인용 해시 (HMAC-SHA256, 16진수 64자). 키를 섞어서 번호 목록으로 역추적하기 어렵게 한다 (NFR-04). */
@Component
public class HmacHasher {

    private final SecretKeySpec key;

    public HmacHasher(@Value("${crm.crypto.hmac-key}") String base64Key) {
        this.key = new SecretKeySpec(Keys.decode(base64Key, "HMAC_KEY", 32, false), "HmacSHA256");
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.");
        }
    }
}

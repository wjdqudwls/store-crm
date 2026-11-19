package com.crmpos.common.crypto;

import java.util.Base64;

/** 환경변수로 받은 base64 키를 바이트로 바꾸고 길이를 확인한다. 키 값은 메시지에 쓰지 않는다. */
final class Keys {

    private Keys() {
    }

    static byte[] decode(String base64, String name, int minBytes, boolean exact) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException(name + " 값이 비어 있습니다 (환경변수로 받습니다).");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " 값이 base64 형식이 아닙니다.");
        }
        if (exact ? raw.length != minBytes : raw.length < minBytes) {
            throw new IllegalArgumentException(name + " 길이는 " + minBytes + "바이트" + (exact ? "여야" : " 이상이어야") + " 합니다.");
        }
        return raw;
    }
}

package com.crmpos.customer.domain;

/**
 * 전화번호를 E.164 형식으로 바꾼다 (BR-01, ADR-019). 라이브러리 없이 규칙만 확인한다.
 * 국내 번호(0으로 시작)는 +82, +로 시작하면 + 와 숫자만 남기고, 숫자는 8~15자리다. 외국인은 국가번호가 있어야 한다.
 */
public final class PhoneNumber {

    private static final int MIN_DIGITS = 8;
    private static final int MAX_DIGITS = 15;

    private PhoneNumber() {
    }

    /** 규칙에 맞지 않으면 null. 입력값은 어디에도 남기지 않는다. */
    public static String normalize(String raw, Nationality nationality) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\s\\-().]", "");
        String digits;
        if (cleaned.startsWith("+")) {
            digits = cleaned.substring(1);
        } else if (cleaned.startsWith("0") && !cleaned.startsWith("00") && nationality != Nationality.FOREIGN) {
            digits = "82" + cleaned.substring(1);
        } else {
            return null;
        }
        if (!digits.matches("[1-9][0-9]*") || digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS) {
            return null;
        }
        return "+" + digits;
    }

    public static String last4(String e164) {
        return e164.substring(e164.length() - 4);
    }

    /** 목록에 보이는 모양 (NFR-05). */
    public static String masked(String last4) {
        return "****-" + last4;
    }
}

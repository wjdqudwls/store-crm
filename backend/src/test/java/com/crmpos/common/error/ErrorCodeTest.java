package com.crmpos.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 03-api.md 5장 에러 코드 표와 ErrorCode가 같은지 확인한다. */
class ErrorCodeTest {

    private static final Map<String, Integer> EXPECTED = Map.ofEntries(
            Map.entry("VALIDATION_FAILED", 400),
            Map.entry("AUTH_INVALID_CREDENTIALS", 401),
            Map.entry("AUTH_TOKEN_INVALID", 401),
            Map.entry("AUTH_TOKEN_EXPIRED", 401),
            Map.entry("AUTH_ACCOUNT_DISABLED", 401),
            Map.entry("AUTH_LOGIN_LOCKED", 429),
            Map.entry("ACCESS_DENIED", 403),
            Map.entry("STORE_REQUIRED", 403),
            Map.entry("CUSTOMER_NOT_FOUND", 404),
            Map.entry("PRODUCT_NOT_FOUND", 404),
            Map.entry("PRODUCT_IMPORT_FAILED", 400),
            Map.entry("PRODUCT_CODE_DUPLICATED", 409),
            Map.entry("SALE_NOT_FOUND", 404),
            Map.entry("STORE_NOT_FOUND", 404),
            Map.entry("STAFF_NOT_FOUND", 404),
            Map.entry("NOTE_NOT_FOUND", 404),
            Map.entry("FOLLOW_UP_NOT_FOUND", 404),
            Map.entry("MEMBER_NOT_FOUND", 404),
            Map.entry("CUSTOMER_PHONE_DUPLICATED", 409),
            Map.entry("STAFF_LOGIN_ID_DUPLICATED", 409),
            Map.entry("RETURN_QTY_EXCEEDED", 409),
            Map.entry("IDEMPOTENCY_KEY_REUSED", 409),
            Map.entry("FOLLOW_UP_ALREADY_CLOSED", 409),
            Map.entry("INVALID_SIZE", 400),
            Map.entry("DAY_CLOSED", 409),
            Map.entry("SALE_LOCKED", 409),
            Map.entry("SALE_ALREADY_VOIDED", 409),
            Map.entry("MANAGER_SLOT_TAKEN", 409),
            Map.entry("HANDOVER_NOT_ALLOWED", 409),
            Map.entry("PASSWORD_POLICY_VIOLATION", 400),
            Map.entry("PASSWORD_MISMATCH", 400),
            Map.entry("TOO_MANY_REQUESTS", 429),
            // B0-2에서 03 5장에 더한 코드
            Map.entry("NOT_FOUND", 404),
            Map.entry("METHOD_NOT_ALLOWED", 405),
            Map.entry("UNSUPPORTED_MEDIA_TYPE", 415),
            Map.entry("NOT_ACCEPTABLE", 406),
            Map.entry("DAY_CLOSE_NOT_FOUND", 404),
            Map.entry("INTERNAL_ERROR", 500));

    @Test
    @DisplayName("ErrorCode는 03-api.md 5장의 코드와 HTTP 상태를 모두 갖는다")
    void should_matchApiDoc_when_listErrorCodes() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(EXPECTED).as("03에 없는 코드 " + code).containsKey(code.name());
            assertThat(code.getStatus().value()).as(code.name()).isEqualTo(EXPECTED.get(code.name()));
        }
        assertThat(ErrorCode.values()).hasSize(EXPECTED.size());
    }

    @Test
    @DisplayName("모든 에러 코드는 비어 있지 않은 기본 메시지를 갖는다")
    void should_haveMessage_when_anyErrorCode() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
        assertThat(ErrorCode.NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

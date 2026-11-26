package com.crmpos.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API 에러 코드 (03-api.md 5장). 코드마다 HTTP 상태와 기본 메시지를 가진다. */
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
    AUTH_ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다."),
    AUTH_LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인을 여러 번 틀려 잠겼습니다. 10분 뒤에 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    STORE_REQUIRED(HttpStatus.FORBIDDEN, "매장 계정으로만 할 수 있습니다."),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_IMPORT_FAILED(HttpStatus.BAD_REQUEST, "엑셀에 잘못된 행이 있어 아무것도 저장하지 않았습니다."),
    PRODUCT_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 품번입니다."),
    SALE_NOT_FOUND(HttpStatus.NOT_FOUND, "판매를 찾을 수 없습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다."),
    NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "메모를 찾을 수 없습니다."),
    FOLLOW_UP_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일을 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "가족 구성원을 찾을 수 없습니다."),
    CUSTOMER_PHONE_DUPLICATED(HttpStatus.CONFLICT, "같은 전화번호의 고객이 이미 있습니다."),
    STAFF_LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 로그인 ID입니다."),
    RETURN_QTY_EXCEEDED(HttpStatus.CONFLICT, "반품할 수 있는 수량을 넘었습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 요청 키로 다른 내용을 보냈습니다."),
    FOLLOW_UP_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 끝난 할 일입니다."),
    INVALID_SIZE(HttpStatus.BAD_REQUEST, "품목에 맞지 않는 사이즈입니다."),
    DAY_CLOSED(HttpStatus.CONFLICT, "마감된 날입니다."),
    SALE_LOCKED(HttpStatus.CONFLICT, "마감된 판매는 고칠 수 없습니다."),
    SALE_ALREADY_VOIDED(HttpStatus.CONFLICT, "이미 기록 취소된 판매입니다."),
    MANAGER_SLOT_TAKEN(HttpStatus.CONFLICT, "매니저 자리가 이미 차 있습니다."),
    HANDOVER_NOT_ALLOWED(HttpStatus.CONFLICT, "넘겨받을 수 없는 계정입니다."),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상, 영문과 숫자를 포함해야 합니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 뒤에 다시 시도해 주세요."),
    // B0-2에서 03 5장에 더한 코드
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다. JSON으로 보내 주세요."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "요청한 응답 형식을 줄 수 없습니다. JSON만 지원합니다."),
    DAY_CLOSE_NOT_FOUND(HttpStatus.NOT_FOUND, "마감 기록이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼습니다. 잠시 뒤에 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}

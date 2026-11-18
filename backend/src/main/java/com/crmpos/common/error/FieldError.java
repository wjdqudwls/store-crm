package com.crmpos.common.error;

/** 입력값 검증 실패 한 칸. 입력한 값은 담지 않는다 (NFR-04b). */
public record FieldError(String field, String reason) {
}

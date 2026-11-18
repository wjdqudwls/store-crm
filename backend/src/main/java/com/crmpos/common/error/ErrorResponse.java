package com.crmpos.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 모든 실패 응답의 모양 (03-api.md 1장). fieldErrors는 400 검증 실패일 때만, details는 필요할 때만 들어간다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Object details,
        String traceId) {

    public static ErrorResponse from(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, null, traceId);
    }

    public static ErrorResponse from(ErrorCode errorCode, Object details, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, details, traceId);
    }

    public static ErrorResponse validation(List<FieldError> fieldErrors, String traceId) {
        return new ErrorResponse(ErrorCode.VALIDATION_FAILED.name(),
                ErrorCode.VALIDATION_FAILED.getMessage(), fieldErrors, null, traceId);
    }
}

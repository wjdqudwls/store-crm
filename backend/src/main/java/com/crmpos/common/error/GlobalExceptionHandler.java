package com.crmpos.common.error;

import com.crmpos.common.web.TraceIdFilter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 03-api.md 1장의 에러 응답으로 바꾼다.
 * 로그에는 코드·필드 이름만 남기고 입력한 값은 남기지 않는다 (NFR-04b).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.info("business error code={}", code);
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.from(code, e.getDetails(), traceId()));
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailed(ValidationFailedException e) {
        log.warn("validation failed fields={}", e.getFieldErrors().stream().map(FieldError::field).toList());
        return validationResponse(e.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        log.warn("validation failed fields={}", fieldErrors.stream().map(FieldError::field).toList());
        return validationResponse(fieldErrors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("validation failed fields=[{}]", e.getParameterName());
        return validationResponse(List.of(new FieldError(e.getParameterName(), "값이 필요합니다.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("validation failed fields=[{}]", e.getName());
        return validationResponse(List.of(new FieldError(e.getName(), "형식이 올바르지 않습니다.")));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        log.warn("validation failed (method parameters)");
        return validationResponse(List.of());
    }

    /** 깨진 JSON·타입이 틀린 값. 예외 메시지에 입력값이 들어 있을 수 있어 로그에도 남기지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("validation failed (unreadable request body)");
        return validationResponse(List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        log.info("no handler for request");
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .body(ErrorResponse.from(ErrorCode.NOT_FOUND, traceId()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.info("method not allowed method={}", e.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ErrorResponse.from(ErrorCode.METHOD_NOT_ALLOWED, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof DataAccessException) {
            // DB 예외 메시지에는 입력값(전화번호 등)이 섞일 수 있어 클래스 이름만 남긴다 (NFR-04b)
            log.error("unexpected database error type={}", e.getClass().getSimpleName());
        } else {
            log.error("unexpected error type={}", e.getClass().getSimpleName(), e);
        }
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.from(ErrorCode.INTERNAL_ERROR, traceId()));
    }

    private ResponseEntity<ErrorResponse> validationResponse(List<FieldError> fieldErrors) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.validation(fieldErrors, traceId()));
    }

    private String traceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}

package com.crmpos.common.error;

import com.crmpos.common.web.TraceIdFilter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.info("unsupported media type");
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ErrorResponse.from(ErrorCode.UNSUPPORTED_MEDIA_TYPE, traceId()));
    }

    /** 파일 올리기 요청에 파일이 없거나, 너무 크거나, multipart 본문이 깨진 경우 */
    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleUpload(Exception e) {
        log.warn("upload failed type={}", e.getClass().getSimpleName());
        String reason = e instanceof MaxUploadSizeExceededException ? "파일이 너무 큽니다 (최대 5MB)." : "파일을 올려 주세요.";
        return validationResponse(List.of(new FieldError("file", reason)));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.info("not acceptable");
        // Accept가 JSON을 못 받는 값이라 오류 본문도 협상에 실패한다. JSON으로 직접 지정해 같은 형식을 준다.
        return ResponseEntity.status(ErrorCode.NOT_ACCEPTABLE.getStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(ErrorCode.NOT_ACCEPTABLE, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof DataAccessException) {
            // DB 예외 메시지에는 입력값(전화번호 등)이 섞일 수 있어 클래스 이름만 남긴다 (NFR-04b)
            log.error("unexpected database error type={}", e.getClass().getSimpleName());
        } else {
            // 예외 메시지에도 입력값이 섞일 수 있어 메시지와 스택 전체는 남기지 않는다. 종류와 우리 코드의 첫 위치만 남긴다 (NFR-04b)
            log.error("unexpected error type={} at={}", e.getClass().getSimpleName(), originOf(e));
        }
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.from(ErrorCode.INTERNAL_ERROR, traceId()));
    }

    private ResponseEntity<ErrorResponse> validationResponse(List<FieldError> fieldErrors) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.validation(fieldErrors, traceId()));
    }

    /** 우리 코드(com.crmpos)에서 처음 나오는 위치 (클래스.메서드:줄). 없으면 첫 프레임. */
    static String originOf(Throwable e) {
        StackTraceElement[] trace = e.getStackTrace();
        for (StackTraceElement frame : trace) {
            if (frame.getClassName().startsWith("com.crmpos")) {
                return simpleClass(frame) + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return trace.length == 0 ? "unknown" : simpleClass(trace[0]) + "." + trace[0].getMethodName();
    }

    private static String simpleClass(StackTraceElement frame) {
        String name = frame.getClassName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private String traceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}

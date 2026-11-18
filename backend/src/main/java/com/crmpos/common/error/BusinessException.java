package com.crmpos.common.error;

import lombok.Getter;

/** 업무 규칙 위반. 에러 코드가 HTTP 상태와 메시지를 정한다. details는 에러별 추가 정보(선택). */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Object details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
}

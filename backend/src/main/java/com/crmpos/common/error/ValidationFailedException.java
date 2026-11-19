package com.crmpos.common.error;

import java.util.List;
import lombok.Getter;

/** 서비스가 찾아낸 입력 오류 목록. 400 VALIDATION_FAILED와 fieldErrors로 바뀐다. 입력값은 담지 않는다. */
@Getter
public class ValidationFailedException extends RuntimeException {

    private final transient List<FieldError> fieldErrors;

    public ValidationFailedException(List<FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_FAILED.getMessage());
        this.fieldErrors = List.copyOf(fieldErrors);
    }
}

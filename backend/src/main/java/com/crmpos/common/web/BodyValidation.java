package com.crmpos.common.web;

import com.crmpos.common.error.FieldError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 요청 본문의 Bean Validation 오류를 {@link FieldError} 목록으로 바꾼다. 컨트롤러의 {@code @Valid}는 첫 단계에서 바로 400을 내서
 * 서비스의 업무 검사(전화번호 형식 등)까지 가지 못하므로, 여러 단계의 오류를 한 번에 모아 주려면 서비스가 직접 부른다.
 * 오류에는 필드 경로와 이유만 담고 입력값은 담지 않는다 (NFR-04b).
 */
public final class BodyValidation {

    private BodyValidation() {
    }

    public static List<FieldError> violations(Validator validator, Object body) {
        List<FieldError> errors = new ArrayList<>();
        for (ConstraintViolation<Object> violation : validator.validate(body)) {
            errors.add(new FieldError(violation.getPropertyPath().toString(), violation.getMessage()));
        }
        errors.sort(Comparator.comparing(FieldError::field).thenComparing(FieldError::reason));
        return errors;
    }

    /** 같은 필드에 이미 오류가 있으면 더하지 않는다 (같은 칸에 이유가 둘 겹쳐 보이지 않게). */
    public static void addIfAbsent(List<FieldError> errors, FieldError error) {
        if (errors.stream().noneMatch(e -> e.field().equals(error.field()))) {
            errors.add(error);
        }
    }
}

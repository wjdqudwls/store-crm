package com.crmpos.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.crmpos.common.web.KstTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 공통 에러 처리를 확인하려고 테스트에서만 쓰는 컨트롤러. 실제 주소는 /api/v1/test/... 가 된다. */
@RestController
class ErrorTestController {

    record PhoneRequest(@NotBlank String name, @NotBlank String phone) {
    }

    @PostMapping("/test/validate")
    Map<String, String> validate(@Valid @RequestBody PhoneRequest request) {
        return Map.of("ok", "true");
    }

    @GetMapping("/test/not-found")
    void notFound() {
        throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }

    @GetMapping("/test/duplicated")
    void duplicated() {
        throw new BusinessException(ErrorCode.CUSTOMER_PHONE_DUPLICATED,
                Map.of("customerId", 1024));
    }

    @GetMapping("/test/boom")
    void boom() {
        throw new IllegalStateException("db password is secret-value");
    }

    record TimeResponse(OffsetDateTime soldAt) {
    }

    @GetMapping("/test/time")
    TimeResponse time() {
        return new TimeResponse(KstTime.toOffset(LocalDateTime.of(2026, 10, 15, 14, 3)));
    }

    @GetMapping("/test/db-error")
    void dbError() {
        throw new DataIntegrityViolationException("Duplicate entry '010-2943-4821' for key 'uk_customer_phone'");
    }

    @GetMapping("/test/ok")
    Map<String, String> ok() {
        return Map.of("ok", "true");
    }
}

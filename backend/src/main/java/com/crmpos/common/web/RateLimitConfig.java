package com.crmpos.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 공개 등록 API의 요청 수 제한 (NFR-04a): 기본은 IP당 1분에 5번. */
@Configuration
public class RateLimitConfig {

    @Bean
    RequestRateLimiter publicRegistrationLimiter(@Value("${crm.public.registration-limit:5}") int limit) {
        return new RequestRateLimiter(limit, 60_000, System::currentTimeMillis);
    }
}

package com.crmpos.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 컨트롤러 단위 테스트(@WebMvcTest)용: 로그인 검사 없이 모두 통과시킨다. 계정은 CurrentAccountProvider를 mock으로
 * 대신하고, 로그인·권한 규칙 자체는 통합 테스트(AuthIntegrationTest)가 실제 규칙으로 확인한다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(PermitAllSecurity.Config.class)
public @interface PermitAllSecurity {

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        SecurityFilterChain permitAllChain(HttpSecurity http) {
            http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .cors(Customizer.withDefaults());
            return http.build();
        }
    }
}

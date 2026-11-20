package com.crmpos.common.security;

import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.ErrorResponse;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인·권한 규칙 (B0-3, AUTH-01·02, 03-api.md 2장). 세션 없이 JWT만 쓴다.
 * - 공개: /api/v1/public/**, /api/v1/auth/login, /api/v1/auth/refresh. /api가 아닌 주소(화면 파일, actuator health,
 *   로컬 Swagger)는 이 필터가 막지 않는다.
 * - KIOSK: /api/v1/kiosk/**, GET /api/v1/me 만. 나머지 API는 KIOSK가 아닌 계정만.
 * 401·403도 다른 오류와 같은 형식(ErrorResponse)으로 준다.
 */
@Configuration
public class SecurityConfig {

    private static final String API = "/api/v1";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, StaffRepository staffRepository,
            ObjectMapper objectMapper) {
        http.csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(API + "/public/**", API + "/auth/login", API + "/auth/refresh").permitAll()
                        .requestMatchers(API + "/kiosk/**").hasRole("KIOSK")
                        .requestMatchers(API + "/me").authenticated()
                        .requestMatchers(API + "/**")
                        .hasAnyRole("HQ", "STORE", "MANAGER", "SENIOR", "JUNIOR", "STAFF")
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> write(response, objectMapper,
                                errorOf(request)))
                        .accessDeniedHandler((request, response, ex) -> write(response, objectMapper,
                                ErrorCode.ACCESS_DENIED)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, staffRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 필터가 남긴 이유(만료·비활성 등)가 있으면 그것을, 없으면 토큰 없음/잘못됨(AUTH_TOKEN_INVALID)을 돌려준다. */
    private static ErrorCode errorOf(HttpServletRequest request) {
        Object reason = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR);
        return reason instanceof ErrorCode code ? code : ErrorCode.AUTH_TOKEN_INVALID;
    }

    private static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.from(code, MDC.get(TraceIdFilter.MDC_KEY))));
    }
}

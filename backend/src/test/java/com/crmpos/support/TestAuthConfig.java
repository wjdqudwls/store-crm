package com.crmpos.support;

import com.crmpos.common.repository.StaffRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 통합 테스트의 MockMvc 요청에 기본으로 강남점 매장 계정(store-gn01) 토큰을 붙인다. 다른 계정은 요청마다
 * {@code .header("Authorization", TestAuth.bearer(id))}로 덮어쓴다. 로그인 자체를 보는 테스트는
 * {@code .header("Authorization", ...)}를 직접 지정하거나 공개 주소를 부른다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAuthConfig {

    @Bean
    MockMvcBuilderCustomizer defaultStoreAccount(StaffRepository staffRepository) {
        // 기본 계정은 앱이 다 켜진 뒤(ApplicationRunner) 만들어지므로, 요청을 보내는 순간에 찾는다
        AtomicReference<String> cached = new AtomicReference<>();
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(request -> {
            if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                String token = cached.updateAndGet(old -> old != null ? old
                        : TestAuth.bearer(staffRepository.findByLoginId("store-gn01").orElseThrow().getId()));
                request.addHeader(HttpHeaders.AUTHORIZATION, token);
            }
            return request;
        }));
    }
}

package com.crmpos.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.security.CurrentAccountProvider;
import com.crmpos.common.security.DevAccountSeeder;
import com.crmpos.support.TestMariaDb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** prod 프로필로 앱을 띄워 운영 설정을 확인한다: 프로브 열림, 다른 actuator·Swagger 닫힘, 임시 계정 없음 (B0-9). */
@SpringBootTest
@Import(TestMariaDb.class)
@ActiveProfiles({"test", "prod"})
@AutoConfigureMockMvc
class ProdProfileTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("health와 liveness·readiness 프로브는 열려 있다")
    void should_openHealthAndProbes() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("health 말고 다른 actuator 주소(env·beans·metrics)는 닫혀 있다")
    void should_closeOtherActuator() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/metrics", "/actuator/info"}) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("API 명세(/v3/api-docs, /swagger-ui)는 운영에서 닫혀 있다")
    void should_closeSwagger() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("임시 계정(DevAccountSeeder·X-Dev-Account-Id)이 없어 로그인 전에는 API가 401이다")
    void should_haveNoDevAccounts() throws Exception {
        assertThat(context.getBeanNamesForType(DevAccountSeeder.class)).isEmpty();
        assertThat(context.getBeanNamesForType(CurrentAccountProvider.class)).isEmpty();

        mockMvc.perform(get("/api/v1/stores").header("X-Dev-Account-Id", "1")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }
}

package com.crmpos.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 화면 경로에서 새로고침하면 index.html을 돌려주고, API·actuator·파일 경로는 그대로 404인지 확인한다 (ADR-013). */
@IntegrationTest
@AutoConfigureMockMvc
class SpaFallbackTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("루트와 화면 경로(/customers/12)는 index.html을 돌려준다")
    void should_returnIndex_when_spaRoute() throws Exception {
        // 루트는 Spring의 환영 페이지가 index.html로 넘긴다(MockMvc는 넘김을 따라가지 않아 본문이 비어 있다)
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/customers/12")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("test index")));
        mockMvc.perform(get("/sales/new")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 API 경로는 index.html이 아니라 JSON 404다")
    void should_return404Json_when_unknownApi() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("없는 actuator·확장자 있는 파일 경로는 index.html 대신 404다")
    void should_return404_when_unknownActuatorOrFile() throws Exception {
        mockMvc.perform(get("/actuator/nothing")).andExpect(status().isNotFound());
        mockMvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("경로 판별: API·actuator·swagger·v3와 확장자 경로는 화면 경로가 아니다")
    void should_classifyRoutes() {
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("customers/12")).isTrue();
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("")).isTrue();
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("api/v1/x")).isFalse();
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("actuator/health")).isFalse();
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("swagger-ui/index.html")).isFalse();
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("v3/api-docs")).isFalse();
        assertThat(SpaFallbackConfig.SpaResolver.isSpaRoute("assets/app.js")).isFalse();
    }
}

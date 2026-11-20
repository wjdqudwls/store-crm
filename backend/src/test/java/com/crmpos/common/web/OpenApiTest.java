package com.crmpos.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 앱 전체를 띄워 Swagger와 헬스 체크 주소를 확인한다 (NFR-12). DB가 필요하다. */
@IntegrationTest
@AutoConfigureMockMvc
class OpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("API 문서(/v3/api-docs)와 /swagger-ui가 열린다")
    void should_openSwagger_when_appStarts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("헬스 체크는 /api/v1 없이 /actuator/health에서 열린다")
    void should_openHealthWithoutPrefix_when_appStarts() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("모든 API에 한글 요약(@Operation)이 있다: 설명이 빠진 새 API를 막는다")
    void should_describeEveryOperation_when_docsOpen() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        var paths = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json).get("paths");
        java.util.List<String> missing = new java.util.ArrayList<>();
        // 오류 형식 테스트용 가짜 컨트롤러(/api/v1/test/**)는 제외한다
        paths.properties().stream().filter(path -> !path.getKey().startsWith("/api/v1/test/")).forEach(path -> path.getValue().properties().forEach(op -> {
            var summary = op.getValue().get("summary");
            if (summary == null || summary.asString().isBlank()) {
                missing.add(op.getKey().toUpperCase() + " " + path.getKey());
            }
        }));
        org.assertj.core.api.Assertions.assertThat(missing).as("@Operation(summary)가 없는 API").isEmpty();
    }
}

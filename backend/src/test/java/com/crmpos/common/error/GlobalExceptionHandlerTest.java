package com.crmpos.common.error;

import com.crmpos.support.PermitAllSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.web.RequestLogFilter;
import com.crmpos.common.web.TraceIdFilter;
import com.crmpos.common.web.WebConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = ErrorTestController.class)
@PermitAllSecurity
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, RequestLogFilter.class, WebConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("필수값이 빠지면 400 VALIDATION_FAILED와 필드별 이유를 준다")
    void should_return400WithFieldErrors_when_validationFails() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김하늘\",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("깨진 JSON이면 400 VALIDATION_FAILED이고 입력한 값이 응답에 없다")
    void should_return400_when_malformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김하늘\", \"phone\": 010-2943-4821"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("2943").doesNotContain("김하늘");
    }

    @Test
    @DisplayName("BusinessException은 에러 코드의 HTTP 상태와 메시지를 주고 fieldErrors·details 키는 없다")
    void should_returnCodeStatus_when_businessException() throws Exception {
        mockMvc.perform(get("/api/v1/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("고객을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("details가 있는 예외는 409와 details를 함께 준다")
    void should_returnDetails_when_exceptionHasDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test/duplicated"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PHONE_DUPLICATED"))
                .andExpect(jsonPath("$.details.customerId").value(1024));
    }

    @Test
    @DisplayName("없는 주소는 404 NOT_FOUND, 허용하지 않는 메서드는 405 METHOD_NOT_ALLOWED를 같은 형식으로 준다")
    void should_returnErrorFormat_when_unknownPathOrMethod() throws Exception {
        mockMvc.perform(get("/api/v1/test/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(post("/api/v1/test/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("JSON이 아닌 본문(Content-Type)은 415 UNSUPPORTED_MEDIA_TYPE을 같은 형식으로 준다")
    void should_return415_when_contentTypeNotSupported() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate").contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                        .content("phone=010"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("JSON을 줄 수 없는 Accept는 406 NOT_ACCEPTABLE을 같은 형식으로 준다")
    void should_return406_when_acceptNotSupported() throws Exception {
        mockMvc.perform(get("/api/v1/test/ok").accept(org.springframework.http.MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR이고 예외 메시지·스택이 응답에 없다")
    void should_hideInternals_when_unexpectedException() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-value").doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("성공·실패 모두 X-Trace-Id 헤더가 있고, 에러 본문의 traceId와 같다")
    void should_sameTraceId_when_headerAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/test/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));

        MvcResult error = mockMvc.perform(get("/api/v1/test/not-found")).andReturn();
        String headerId = error.getResponse().getHeader("X-Trace-Id");
        assertThat(headerId).isNotBlank();
        mockMvc.perform(get("/api/v1/test/not-found"))
                .andExpect(header().exists("X-Trace-Id"));
        assertThat(error.getResponse().getContentAsString()).contains("\"traceId\":\"" + headerId + "\"");
    }

    @Test
    @DisplayName("요청에 담아 보낸 X-Trace-Id는 쓰지 않고 서버가 새로 만든다")
    void should_ignoreIncomingTraceId_when_requestHasHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/ok").header("X-Trace-Id", "attacker-value"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Trace-Id")).isNotEqualTo("attacker-value");
    }

    @Test
    @DisplayName("요청 로그에는 메서드·경로·상태·시간·traceId만 남는다")
    void should_logOnlySafeFields_when_request(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/ok?phone=010-2943-4821")).andReturn();
        String traceId = result.getResponse().getHeader("X-Trace-Id");

        assertThat(output.getAll())
                .contains("GET /api/v1/test/ok 200")
                .contains("traceId=" + traceId)
                .doesNotContain("2943");
    }

    @Test
    @DisplayName("검증 실패 로그에는 필드 이름만 있고 입력한 값은 없다")
    void should_logFieldNamesOnly_when_validationFails(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"김하늘\",\"phone\":\"\"}"));

        assertThat(output.getAll())
                .contains("phone")
                .doesNotContain("김하늘");
    }

    @Test
    @DisplayName("응답의 시각은 +09:00이 붙은 ISO 형식이다")
    void should_writeKstOffset_when_responseHasTime() throws Exception {
        mockMvc.perform(get("/api/v1/test/time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldAt").value("2026-10-15T14:03:00+09:00"));
    }

    @Test
    @DisplayName("DB 예외가 나면 로그에 예외 클래스 이름만 남고 메시지(입력값)는 남지 않는다")
    void should_notLogDbMessage_when_dataAccessException(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/test/db-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(output.getAll())
                .contains("DataIntegrityViolationException")
                .doesNotContain("2943");
    }

    @Test
    @DisplayName("예상 못 한 예외는 로그에 종류와 우리 코드의 위치만 남고 예외 메시지(입력값)는 남지 않는다")
    void should_logTypeAndOriginOnly_when_unexpectedException(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/test/boom")).andExpect(status().isInternalServerError());

        assertThat(output.getAll())
                .contains("unexpected error type=IllegalStateException at=ErrorTestController.boom")
                .doesNotContain("secret-value");
    }
}

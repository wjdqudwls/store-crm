package com.crmpos.customer.controller;

import com.crmpos.support.PermitAllSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.security.CurrentAccountProvider;
import com.crmpos.customer.service.CustomerQueryService;
import com.crmpos.customer.service.CustomerRegistrationService;
import com.crmpos.customer.service.CustomerUpdateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@PermitAllSecurity
@ExtendWith(OutputCaptureExtension.class)
class CustomerControllerTest {

    private static final String BODY = """
            {"name":"김하늘","phone":"010-2943-4821","nationality":"DOMESTIC",
             "agreements":{"privacy":true,"age14":true,"marketingSms":true,"marketingKakao":false},
             "address":{"sido":"서울","gu":"강남구","detail":"테헤란로 123 401호"},"gender":"F",
             "sizes":{"top":"M","outer":"90","bottom":"26","shoes":"240"}}""";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerRegistrationService service;

    @MockitoBean
    CustomerQueryService queryService;

    @MockitoBean
    CustomerUpdateService updateService;

    @MockitoBean
    CurrentAccountProvider accountProvider;

    private void loginAs(StaffRole role) {
        given(accountProvider.current(any())).willReturn(new CurrentAccount(2L, role, 3L));
    }

    @BeforeEach
    void defaultStore() {
        loginAs(StaffRole.STORE);
    }

    private org.springframework.test.web.servlet.ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("매장 계정이 등록하면 201과 {id, name, phoneMasked}를 돌려준다")
    void should_return201_when_storeRegisters() throws Exception {
        given(service.register(any(), any())).willReturn(new CustomerCreatedResponse(1024L, "김하늘", "****-4821"));

        register(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1024))
                .andExpect(jsonPath("$.phoneMasked").value("****-4821"));
    }

    @Test
    @DisplayName("매장 계정이 아니면 403 ACCESS_DENIED")
    void should_return403_when_notStoreAccount() throws Exception {
        for (StaffRole role : List.of(StaffRole.HQ, StaffRole.STAFF, StaffRole.KIOSK)) {
            loginAs(role);
            register(BODY).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    @DisplayName("같은 번호가 있으면 409와 기존 고객 details")
    void should_return409_when_phoneDuplicated() throws Exception {
        given(service.register(any(), any())).willThrow(new BusinessException(ErrorCode.CUSTOMER_PHONE_DUPLICATED,
                Map.of("customerId", 1024L, "name", "김하늘", "phoneMasked", "****-4821")));

        register(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PHONE_DUPLICATED"))
                .andExpect(jsonPath("$.details.customerId").value(1024))
                .andExpect(jsonPath("$.details.phoneMasked").value("****-4821"));
    }

    @Test
    @DisplayName("서비스가 찾은 입력 오류는 400 fieldErrors로 나간다")
    void should_return400_when_serviceFindsFieldErrors() throws Exception {
        given(service.register(any(), any())).willThrow(
                new ValidationFailedException(List.of(new FieldError("sizes.outer", "이 품목의 사이즈 목록에 없습니다."))));

        register(BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sizes.outer"));
    }

    @Test
    @DisplayName("요청·오류 처리 로그에 이름·전화번호·주소가 남지 않는다")
    void should_notLogPersonalData(CapturedOutput output) throws Exception {
        given(service.register(any(), any())).willThrow(
                new ValidationFailedException(List.of(new FieldError("phone", "전화번호 형식이 올바르지 않습니다."))));
        register(BODY);
        register(BODY.replace("\"privacy\":true", "\"privacy\":false"));
        register("{broken " + "010-2943-4821");

        assertThat(output.getAll()).doesNotContain("2943").doesNotContain("김하늘").doesNotContain("테헤란로");
    }

    @Test
    @DisplayName("요청 record의 toString은 개인정보를 보이지 않는다")
    void should_maskPersonalData_when_requestToString() {
        CustomerRegisterRequest request = new CustomerRegisterRequest("김하늘", null, "010-2943-4821", null,
                new CustomerRegisterRequest.Agreements(true, true, true, true), 1991, "0314",
                new CustomerRegisterRequest.Address("서울", "강남구", "테헤란로 123 401호"), null, null, null, null, null);

        assertThat(request.toString()).doesNotContain("김하늘").doesNotContain("2943").doesNotContain("테헤란로");
        assertThat(request.address().toString()).doesNotContain("테헤란로").doesNotContain("강남구");
    }
}

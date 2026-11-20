package com.crmpos.common.security;

import com.crmpos.support.PermitAllSecurity;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountTestController.class)
@PermitAllSecurity
class CurrentAccountResolverTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CurrentAccountProvider provider;

    @Test
    @DisplayName("컨트롤러가 CurrentAccount 인자로 계정의 매장 ID를 받는다")
    void should_injectCurrentAccount_when_controllerAsksForIt() throws Exception {
        given(provider.current(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CurrentAccount(2L, StaffRole.STORE, 9L));

        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(9))
                .andExpect(jsonPath("$.role").value("STORE"));
    }

    @Test
    @DisplayName("계정을 못 꺼내면 401 AUTH_TOKEN_INVALID, 같은 에러 형식")
    void should_return401_when_providerThrows() throws Exception {
        given(provider.current(org.mockito.ArgumentMatchers.any()))
                .willThrow(new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));

        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}

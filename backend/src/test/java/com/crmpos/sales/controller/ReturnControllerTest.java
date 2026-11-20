package com.crmpos.sales.controller;

import com.crmpos.support.PermitAllSecurity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.security.CurrentAccountProvider;
import com.crmpos.sales.domain.SaleStatus;
import com.crmpos.sales.service.ReturnRecordingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(ReturnController.class)
@PermitAllSecurity
class ReturnControllerTest {

    private static final String KEY = "3d8a2f4b-6c1e-4b7a-8e2d-5f9c0a1b2c3d";
    private static final String BODY = """
            {"staffId":17,"lines":[{"lineId":2,"qty":1}]}""";

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    ReturnRecordingService service;
    @MockitoBean
    CurrentAccountProvider accountProvider;

    @BeforeEach
    void defaultStore() {
        given(accountProvider.current(any())).willReturn(new CurrentAccount(3L, StaffRole.STORE, 1L));
    }

    private ResultActions send(String key, String body) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/v1/sales/88231/returns")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private static ReturnRecordingService.Result result(boolean created) {
        return new ReturnRecordingService.Result(new ReturnResponse(5021L, 217800, SaleStatus.PARTIAL_RETURNED), created);
    }

    @Test
    @DisplayName("처음 반품하면 201과 {returnId, returnedAmount, status}")
    void should_return201() throws Exception {
        given(service.record(eq(88231L), eq(KEY), any(), any())).willReturn(result(true));

        send(KEY, BODY).andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnId").value(5021))
                .andExpect(jsonPath("$.returnedAmount").value(217800))
                .andExpect(jsonPath("$.status").value("PARTIAL_RETURNED"));
    }

    @Test
    @DisplayName("같은 키로 다시 보내면 200")
    void should_return200_whenReplayed() throws Exception {
        given(service.record(eq(88231L), eq(KEY), any(), any())).willReturn(result(false));

        send(KEY, BODY).andExpect(status().isOk());
    }

    @Test
    @DisplayName("줄이 없거나 수량이 0이거나 처리 직원이 없으면 400 fieldErrors")
    void should_return400_whenBodyInvalid() throws Exception {
        send(KEY, """
                {"lines":[]}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='staffId')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='lines')]").exists());
        send(KEY, """
                {"staffId":17,"lines":[{"lineId":2,"qty":0}]}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='lines[0].qty')]").exists());
    }

    @Test
    @DisplayName("남은 수량 초과·마감·기록 취소는 409, 다른 매장은 403, 없는 판매는 404")
    void should_mapBusinessErrors() throws Exception {
        given(service.record(any(), any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RETURN_QTY_EXCEEDED))
                .willThrow(new BusinessException(ErrorCode.DAY_CLOSED))
                .willThrow(new BusinessException(ErrorCode.SALE_ALREADY_VOIDED))
                .willThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                .willThrow(new BusinessException(ErrorCode.STORE_REQUIRED))
                .willThrow(new BusinessException(ErrorCode.SALE_NOT_FOUND));

        send(KEY, BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_QTY_EXCEEDED"));
        send(KEY, BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        send(KEY, BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_ALREADY_VOIDED"));
        send(KEY, BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        send(KEY, BODY).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        send(KEY, BODY).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_REQUIRED"));
        send(KEY, BODY).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
    }
}

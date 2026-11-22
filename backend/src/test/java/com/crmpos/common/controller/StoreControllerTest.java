package com.crmpos.common.controller;

import com.crmpos.support.PermitAllSecurity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.security.CurrentAccountProvider;
import com.crmpos.common.service.StoreQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreController.class)
@PermitAllSecurity
class StoreControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    StoreQueryService service;
    @MockitoBean
    CurrentAccountProvider accountProvider;

    @BeforeEach
    void defaultHq() {
        given(accountProvider.current(any())).willReturn(new CurrentAccount(1L, StaffRole.HQ, null));
    }

    @Test
    @DisplayName("GET /stores는 200과 매장 정보·활성 직원 수")
    void should_return200_storeList() throws Exception {
        given(service.listStores(any())).willReturn(List.of(new StoreItem(1L, "GN01", "강남점", 4)));

        mockMvc.perform(get("/api/v1/stores")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1)).andExpect(jsonPath("$[0].code").value("GN01"))
                .andExpect(jsonPath("$[0].name").value("강남점")).andExpect(jsonPath("$[0].activeStaffCount").value(4));
    }

    @Test
    @DisplayName("GET /stores/{id}/staff는 200과 계정 목록, 비밀번호 값은 없다")
    void should_return200_staffList() throws Exception {
        given(service.listStaff(any(), any())).willReturn(List
                .of(new StaffItem(11L, "박서연", StaffRole.MANAGER, ManagerSlot.MAIN, "gn01-seoyeon", "S011", true)));

        mockMvc.perform(get("/api/v1/stores/1/staff")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11)).andExpect(jsonPath("$[0].role").value("MANAGER"))
                .andExpect(jsonPath("$[0].managerSlot").value("MAIN"))
                .andExpect(jsonPath("$[0].loginId").value("gn01-seoyeon"))
                .andExpect(jsonPath("$[0].staffCode").value("S011")).andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("권한이 없으면 403 ACCESS_DENIED, 없는 매장이면 404 STORE_NOT_FOUND")
    void should_return403And404() throws Exception {
        given(service.listStores(any())).willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));
        given(service.listStaff(any(), any())).willThrow(new BusinessException(ErrorCode.STORE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/stores")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/stores/999/staff")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /stores/{id}는 200과 매장 정보·활성 직원 수, 권한·없는 매장은 403·404")
    void should_returnStoreDetail() throws Exception {
        given(service.getStore(org.mockito.ArgumentMatchers.eq(1L), any())).willReturn(new StoreItem(1L, "GN01", "강남점", 4));
        given(service.getStore(org.mockito.ArgumentMatchers.eq(2L), any())).willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));
        given(service.getStore(org.mockito.ArgumentMatchers.eq(999L), any())).willThrow(new BusinessException(ErrorCode.STORE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/stores/1")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GN01"))
                .andExpect(jsonPath("$.activeStaffCount").value(4));
        mockMvc.perform(get("/api/v1/stores/2")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/stores/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }
}

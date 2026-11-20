package com.crmpos.customer.controller;

import com.crmpos.support.PermitAllSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.security.CurrentAccountProvider;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.service.CustomerQueryService;
import com.crmpos.customer.service.CustomerRegistrationService;
import com.crmpos.customer.service.CustomerUpdateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 고객 검색·상세·수정의 입력 검증, 권한, 에러 형식 (CUS-01·02·04). */
@WebMvcTest(CustomerController.class)
@PermitAllSecurity
class CustomerReadUpdateControllerTest {

    private static final String PATCH_BODY = """
            {"name":"김하늘","phone":"010-2943-4821","nationality":"DOMESTIC","gender":"F",
             "marketing":{"sms":true,"kakao":false}}""";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerRegistrationService registrationService;
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

    private static CustomerDetailResponse detail() {
        return new CustomerDetailResponse(1024L, "김하늘", null, com.crmpos.customer.domain.Nationality.DOMESTIC,
                "010-2943-4821", null, new CustomerDetailResponse.Ref(1L, "강남점"), null,
                new CustomerDetailResponse.Grade(GradeCode.NORMAL, GradeCode.NORMAL, GradeCode.NORMAL, "CONFIRMED",
                        "26/27", 0, 0, 0, 0, 0, 0, 1, null),
                new CustomerDetailResponse.Stats(null, null, 0, 0),
                new CustomerDetailResponse.Profile(null, null, null, null, null, List.of(),
                        new CustomerDetailResponse.Sizes(null, null, null, null), null),
                new CustomerDetailResponse.Marketing(false, false), null);
    }

    @Test
    @DisplayName("이름으로 검색하면 목록 형식으로 돌려주고 번호는 뒷자리만 보인다")
    void should_returnPage_when_search() throws Exception {
        given(queryService.search("김하늘", 0, 20)).willReturn(PageResponse.of(
                List.of(new CustomerListItem(1024L, "김하늘", "****-4821", GradeCode.B, null, "이지은")), 0, 20, 1));

        mockMvc.perform(get("/api/v1/customers").param("name", "김하늘"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].phoneMasked").value("****-4821"))
                .andExpect(jsonPath("$.content[0].staffName").value("이지은"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("직원 계정도 검색·상세를 볼 수 있지만 키오스크 계정은 403")
    void should_allowStaffButNotKiosk() throws Exception {
        given(queryService.search(any(), eq(0), eq(20))).willReturn(PageResponse.of(List.of(), 0, 20, 0));
        loginAs(StaffRole.STAFF);
        mockMvc.perform(get("/api/v1/customers").param("name", "김")).andExpect(status().isOk());

        loginAs(StaffRole.KIOSK);
        mockMvc.perform(get("/api/v1/customers").param("name", "김")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/1024")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("한 번에 100개를 넘겨 달라고 하면 400")
    void should_return400_when_sizeTooBig() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("name", "김").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("상세는 include=address일 때만 주소를 요청한다")
    void should_passIncludeAddress() throws Exception {
        given(queryService.get(1024L, false)).willReturn(detail());
        given(queryService.get(1024L, true)).willReturn(detail());

        mockMvc.perform(get("/api/v1/customers/1024")).andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("010-2943-4821"))
                .andExpect(jsonPath("$.grade.season").value("26/27"))
                .andExpect(jsonPath("$.addressDetail").doesNotExist());
        mockMvc.perform(get("/api/v1/customers/1024").param("include", "address")).andExpect(status().isOk());

        org.mockito.Mockito.verify(queryService).get(1024L, true);
    }

    @Test
    @DisplayName("없는 고객 상세는 404 CUSTOMER_NOT_FOUND")
    void should_return404_when_customerMissing() throws Exception {
        given(queryService.get(99L, false)).willThrow(new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("매장·본사 계정은 수정할 수 있다 (200)")
    void should_return200_when_storeOrHqUpdates() throws Exception {
        given(updateService.update(eq(1024L), any(), any())).willReturn(detail());
        for (StaffRole role : List.of(StaffRole.STORE, StaffRole.HQ)) {
            loginAs(role);
            mockMvc.perform(patch("/api/v1/customers/1024").contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("직원·키오스크 계정이 수정하면 403")
    void should_return403_when_staffUpdates() throws Exception {
        for (StaffRole role : List.of(StaffRole.MANAGER, StaffRole.STAFF, StaffRole.KIOSK)) {
            loginAs(role);
            mockMvc.perform(patch("/api/v1/customers/1024").contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    @DisplayName("바꾼 번호가 다른 고객 것이면 409와 그 고객 정보")
    void should_return409_when_phoneBelongsToOther() throws Exception {
        given(updateService.update(eq(1024L), any(), any())).willThrow(new BusinessException(
                ErrorCode.CUSTOMER_PHONE_DUPLICATED, Map.of("customerId", 2310L, "name", "박도윤",
                        "phoneMasked", "****-0937")));

        mockMvc.perform(patch("/api/v1/customers/1024").contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.customerId").value(2310));
    }

    @Test
    @DisplayName("응답·수정 요청 record의 toString은 개인정보를 보이지 않는다")
    void should_maskPersonalData_inToString() {
        CustomerUpdateRequest request = new CustomerUpdateRequest("김하늘", null, "010-2943-4821", null, null, null,
                new CustomerRegisterRequest.Address("서울", "강남구", "테헤란로 1"), null, null, null, null, null,
                new CustomerUpdateRequest.Marketing(true, true));

        assertThat(request.toString()).doesNotContain("김하늘").doesNotContain("2943");
        assertThat(detail().toString()).doesNotContain("김하늘").doesNotContain("2943");
    }
}

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
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.sales.domain.SaleKind;
import com.crmpos.sales.service.SaleRecordingService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(SaleController.class)
@PermitAllSecurity
class SaleControllerTest {

    private static final String KEY = "6b0f6c1e-2f7a-4f3c-9a51-1d2c3e4f5a6b";
    private static final String BODY = """
            {"sellerStaffId":17,"customerId":1024,
             "lines":[{"skuId":5501,"qty":1,"lineAmount":774060},
                      {"productName":"캐시미어 머플러","itemType":"ACC","size":"FREE","color":"오트밀","qty":1,"lineAmount":217800}],
             "memo":"요청했던 롱코트 구매"}""";

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    SaleRecordingService service;
    @MockitoBean
    CurrentAccountProvider accountProvider;

    @BeforeEach
    void defaultStore() {
        given(accountProvider.current(any())).willReturn(new CurrentAccount(3L, StaffRole.STORE, 1L));
    }

    private static SaleResponse response() {
        return new SaleResponse(88231L, "GN01-20261015-0012", OffsetDateTime.parse("2026-10-15T14:03:00+09:00"),
                SaleKind.NORMAL, 17L, 1024L, GradeCode.B,
                List.of(new SaleResponse.Line(1L, 5501L, "D2110", "울 블렌드 롱코트", ItemType.OUTER, "차콜", "90",
                        798000L, 1, 774060, null, null)), 774060, 1);
    }

    private ResultActions send(String key, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("처음 저장하면 201과 영수증 번호·합계를 돌려준다")
    void should_return201_when_created() throws Exception {
        given(service.record(eq(KEY), any(), any())).willReturn(new SaleRecordingService.Result(response(), true));

        send(KEY, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNo").value("GN01-20261015-0012"))
                .andExpect(jsonPath("$.totalAmount").value(774060))
                .andExpect(jsonPath("$.appliedGrade").value("B"))
                .andExpect(jsonPath("$.lines[0].listPrice").value(798000));
    }

    @Test
    @DisplayName("같은 키로 다시 보내면 200으로 같은 응답이다")
    void should_return200_when_replayed() throws Exception {
        given(service.record(eq(KEY), any(), any())).willReturn(new SaleRecordingService.Result(response(), false));

        send(KEY, BODY).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(88231));
    }

    @Test
    @DisplayName("요청 키가 없거나 형식이 틀리면 서비스가 400을 낸다")
    void should_return400_when_keyInvalid() throws Exception {
        given(service.record(any(), any(), any())).willThrow(new com.crmpos.common.error.ValidationFailedException(
                List.of(new com.crmpos.common.error.FieldError("Idempotency-Key", "UUID 형식의 요청 키가 필요합니다."))));

        send(null, BODY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
    }

    @Test
    @DisplayName("라인이 없거나 판매 사원이 없으면 400 fieldErrors")
    void should_return400_when_bodyInvalid() throws Exception {
        send(KEY, """
                {"lines":[]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='sellerStaffId')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='lines')]").exists());
    }

    @Test
    @DisplayName("수량이 0이거나 결제 금액이 음수이면 400")
    void should_return400_when_qtyOrAmountInvalid() throws Exception {
        send(KEY, """
                {"sellerStaffId":17,"lines":[{"skuId":5501,"qty":0,"lineAmount":-1}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='lines[0].qty')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='lines[0].lineAmount')]").exists());
    }

    @Test
    @DisplayName("매장 계정이 아니면 403 STORE_REQUIRED")
    void should_return403_when_storeRequired() throws Exception {
        given(service.record(any(), any(), any())).willThrow(new BusinessException(ErrorCode.STORE_REQUIRED));

        send(KEY, BODY).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_REQUIRED"));
    }

    @Test
    @DisplayName("같은 키로 다른 내용은 409, 마감된 날은 409")
    void should_return409() throws Exception {
        given(service.record(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED))
                .willThrow(new BusinessException(ErrorCode.DAY_CLOSED));

        send(KEY, BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        send(KEY, BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
    }

    @Test
    @DisplayName("없는 상품·고객·구성원은 404")
    void should_return404() throws Exception {
        given(service.record(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                .willThrow(new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND))
                .willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        send(KEY, BODY).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        send(KEY, BODY).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        send(KEY, BODY).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }
}
